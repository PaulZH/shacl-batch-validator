package zone.cogni.shacl_validator;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.RDFVisitor;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.shacl.vocabulary.SH;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("ClassHasNoToStringMethod")
class Report {

  private final Model model;
  private final List<String> columns;
  private final List<String> sorting;

  Report(Model model, List<String> columns, List<String> sorting) {
    this.model = model;
    this.columns = columns;
    this.sorting = sorting;
  }

  public Model getModel() {
    return model;
  }

  public long size() {
    return getSeverityCounts().stream()
            .mapToLong(SeverityCount::getCount)
            .sum();
  }

  public List<SeverityCount> getSeverityCounts() {
    List<String> severities = ValidationService.getSeverities();
    Collections.reverse(severities);

    return severities.stream()
            .map(severity -> new SeverityCount(severity, getSeverityCount(severity)))
            .collect(Collectors.toList());
  }

  public List<ReportLine> getReportLines() {
    Set<Statement> statements = model.listStatements(null, RDF.type, SH.AbstractResult).toSet();
    statements.addAll(model.listStatements(null, RDF.type, SH.ValidationResult).toSet());

    List<ReportLine> reportLines = statements.stream()
            .map(Statement::getSubject)
            .map(result -> new ReportLine(model, result))
            .collect(Collectors.toList());

    reportLines.sort(ReportLine.getComparator(sorting));

    return reportLines;
  }

  public List<String> getColumnHeaders() {
    return columns.stream()
            .map(this::getHumanReadableString)
            .collect(Collectors.toList());
  }

  private String getHumanReadableString(String camelCase) {
    String trim = camelCase.trim();
    if (StringUtils.isBlank(trim)) return "";

    String result = String.valueOf(trim.charAt(0));
    for (int i = 1; i < trim.length(); i++) {
      char c = trim.charAt(i);
      result += Character.isUpperCase(c) ? " " + Character.toLowerCase(c)
                                         : String.valueOf(c);
    }

    return result;
  }

  public long getSeverityCount(String severity) {
    Resource severityResource = ResourceFactory.createResource(SH.NS + severity);

    Set<Statement> severityStatements = model.listStatements(null, SH.severity, severityResource).toSet();
    severityStatements.addAll(model.listStatements(null, SH.resultSeverity, severityResource).toSet());

    return severityStatements.stream().map(Statement::getSubject).count();
  }

  private static class ToRdfString implements RDFVisitor {
    @Override
    public Object visitBlank(Resource r, AnonId id) {
      return id.getLabelString();
    }

    @Override
    public Object visitURI(Resource r, String uri) {
      return uri;
    }

    @Override
    public Object visitLiteral(Literal l) {
      return l.getLexicalForm();
    }
  }

  private static class SeverityCount {

    private final String severity;
    private final long count;

    private SeverityCount(String severity, long count) {
      this.severity = severity;
      this.count = count;
    }

    public String getSeverity() {
      return severity;
    }

    public long getCount() {
      return count;
    }

    @Override
    public String toString() {
      return "SeverityCount{" + "severity='" + severity + '\'' +
              ", count=" + count +
              '}';
    }
  }

  private static class ReportLine {

    public static Comparator<ReportLine> getComparator(List<String> sorting) {

      List<Property> properties = sorting.stream()
              .map(name -> ResourceFactory.createProperty(SH.NS + name))
              .collect(Collectors.toList());

      return new Comparator<ReportLine>() {

        private final List<Property> sortProperties = properties;

        @Override
        public int compare(ReportLine left, ReportLine right) {
          for (Property sortProperty : sortProperties) {
            RDFNode leftValue = left.getValue(sortProperty);
            RDFNode rightValue = right.getValue(sortProperty);

            if (leftValue == null && rightValue == null) continue;

            if (leftValue == null) return -1;
            if (rightValue == null) return 1;

            int classComparison = leftValue.getClass().getName().compareTo(rightValue.getClass().getName());
            if (classComparison != 0) return classComparison;

            String leftValueString = (String) leftValue.visitWith(new ToRdfString());
            String rightValueString = (String) rightValue.visitWith(new ToRdfString());

            int valueComparison = leftValueString.compareTo(rightValueString);
            if (valueComparison != 0) return valueComparison;
          }

          return 0;
        }
      };
    }

    private final Model model;
    private final Resource result;

    public ReportLine(Model model, Resource result) {
      this.model = model;
      this.result = result;
    }

    private RDFNode getValue(Property property) {
      List<RDFNode> rdfNodes = model.listObjectsOfProperty(result, property).toList();
      return rdfNodes.isEmpty() ? null : rdfNodes.get(0);
    }

    public Resource getResult() {
      return result;
    }

    public String getStartValue(String shortProperty) {
      Property property = ResourceFactory.createProperty(SH.NS + shortProperty);
      RDFNode value = getValue(property);

      if (value == null) return null;

      if (value instanceof Resource) {
        String uri = (String) value.visitWith(new ToRdfString());
        return uri.contains("#") ? StringUtils.substringBeforeLast(uri, "#") + "#"
                                 : StringUtils.substringBeforeLast(uri, "/") + "/";
      }

      return null;
    }

    public String getValue(String shortProperty) {
      Property property = ResourceFactory.createProperty(SH.NS + shortProperty);
      RDFNode value = getValue(property);

      if (value instanceof Resource) {
        String uri = (String) value.visitWith(new ToRdfString());
        return uri.contains("#") ? StringUtils.substringAfterLast(uri, "#")
                                 : StringUtils.substringAfterLast(uri, "/");
      }

      return value == null ? ""
                           : (String) value.visitWith(new ToRdfString());
    }

  }


}
