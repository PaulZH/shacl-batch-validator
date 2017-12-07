package zone.cogni.shacl_validator;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ext.com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.topbraid.shacl.validation.ValidationUtil;
import org.topbraid.shacl.vocabulary.SH;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


@Service
public class ValidationService {

  private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

  private final ThymeleafService thymeleafService;

  public static List<Resource> getResources(String path) {
    try {
      PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
      Resource[] resources = resolver.getResources("file:" + path);

      List<Resource> result = Arrays.asList(resources);
      result.sort(Comparator.comparing(o -> o.getFilename().toLowerCase()));

      return result;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public ValidationService(ThymeleafService thymeleafService) {
    this.thymeleafService = thymeleafService;
  }


  public void run(String validate, String shacl, String destination, boolean outputHtml, String severity) {
    List<Resource> validateResources = getResources(validate);
    List<Resource> shaclResource = getResources(shacl);

    validate(validateResources, shaclResource, destination, outputHtml, severity);
  }

  private void validate(List<Resource> validateResources, List<Resource> shaclResources, String destination, boolean outputHtml, String severity) {

    log.info("Shacl shapes files: {}.", shaclResources);
    Model shaclModel = JenaUtils.read(shaclResources);

    log.info("Shapes model size: {}", shaclModel.size());

    for (Resource validateResource : validateResources) {
      log.info("Validation of {}", validateResource);
      Model dataModel = JenaUtils.read(validateResource);

      log.info("Number of triples to be validated: {}", dataModel.size());
      if (dataModel.isEmpty()) log.error("File did not contain any triples.");

      Model reportModel = validate(dataModel, shaclModel);
      saveReport(validateResource, reportModel, destination, outputHtml, severity);
    }
  }

  private void saveReport(Resource validateResource, Model reportModel, String destination, boolean outputHtml, String severity) {
    if (!shouldOutputReport(reportModel, severity)) {
      log.info("Not reaching severity level. No report is created. Report size is {}.", reportModel.size());
      return;
    }

    saveRdfReport(validateResource, reportModel, destination);
    saveHtmlReport(validateResource, reportModel, destination, outputHtml);
  }

  private void saveHtmlReport(Resource validateResource, Model reportModel, String destination, boolean outputHtml) {
    if (!outputHtml) return;

    File reportFile = getReportFile(validateResource, destination, "html");
    log.info("Creating HTML report '{}'. Report size is {}.", reportFile, reportModel.size());

    // TODO UTF8!!
    try (FileWriter out = new FileWriter(reportFile)) {
      out.write(thymeleafService.process());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void saveRdfReport(Resource validateResource, Model reportModel, String destination) {
    File reportFile = getReportFile(validateResource,  destination,"ttl");
    log.info("Creating RDF report '{}'. Report size is {}.", reportFile, reportModel.size());
    try (FileOutputStream out = new FileOutputStream(reportFile)) {
      reportModel.write(out, "ttl");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean shouldOutputReport(Model reportModel, String limitSeverity) {
    List<String> severities = getLevelsToCheck(limitSeverity);

    // always output if level is not set
    if (severities.isEmpty()) return true;

    for (String severity : severities) {
      org.apache.jena.rdf.model.Resource severityResource = ResourceFactory.createResource(SH.NS + severity);
      boolean hasMatch = reportModel.listStatements(null, SH.severity, severityResource).hasNext()
              || reportModel.listStatements(null, SH.resultSeverity, severityResource).hasNext();

      if (hasMatch) return true;
    }

    return false;
  }

  private List<String> getLevelsToCheck(String severity) {
    if (severity == null) return Collections.emptyList();

    List<String> severities = Arrays.asList("Info", "Warning", "Violation");
    return severities.subList(severities.indexOf(severity), severities.size());
  }


  private File getReportFile(Resource validateResource, String destination, String fileExtension) {
    File root = getDestinationFolder(destination);

    String filename = validateResource.getFilename();
    String reportFileName = StringUtils.substringBeforeLast(filename, ".") + ".report." + fileExtension;
    return new File(root, reportFileName);
  }

  private File getDestinationFolder(String destination) {
    File root = destination == null ? new File(".")
                                    : new File(destination);

    Preconditions.checkState(root.exists(), "Destination '" + root.getName() + "' not found.");
    Preconditions.checkState(root.isDirectory(), "Destination '" + root.getName() + "' not a directory.");
    return root;
  }

  private Model validate(Model dataModel, Model shapes) {
    return ValidationUtil.validateModel(dataModel, shapes, true).getModel();
  }

}
