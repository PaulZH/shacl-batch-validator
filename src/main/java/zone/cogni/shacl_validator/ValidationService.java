package zone.cogni.shacl_validator;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ext.com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.topbraid.shacl.validation.ValidationUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class ValidationService {

  private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

  public static List<String> getSeverities() {
    return Arrays.asList("Info", "Warning", "Violation");
  }

  public static List<Resource> getResources(String paths) {
    return getPaths(paths).stream()
            .map(ValidationService::getResourcesForOne)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
  }

  private static List<Resource> getResourcesForOne(String path) {
    if (StringUtils.isBlank(path)) return Collections.emptyList();

    if (path.startsWith("http")) {
      return returnUrlResources(path);
    }

    return returnFileResources(path);
  }

  private static List<Resource> returnUrlResources(String path) {
    try {
      UrlHeadersResource resource = new UrlHeadersResource(path)
              .setHeader("Accept", "text/turtle,application/rdf+xml,text/plain");
      return Collections.singletonList(resource);
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private static List<Resource> returnFileResources(String path) {
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

  private static List<String> getPaths(String path) {
    String[] paths = StringUtils.split(path, ",");
    return Arrays.stream(paths)
            .map(s -> StringUtils.trim(s))
            .filter(s -> StringUtils.isNotBlank(s))
            .collect(Collectors.toList());
  }

  private final ThymeleafService thymeleafService;

  public ValidationService(ThymeleafService thymeleafService) {
    this.thymeleafService = thymeleafService;
  }


  public void run(String validate, String shacl, String destination,
                  boolean outputHtml, List<String> columns, List<String> sorting,
                  String severity) {
    List<Resource> validateResources = getResources(validate);
    List<Resource> shaclResource = getResources(shacl);

    validate(validateResources, shaclResource, destination, outputHtml, columns, sorting, severity);
  }

  private void validate(List<Resource> validateResources, List<Resource> shaclResources, String destination,
                        boolean outputHtml, List<String> columns, List<String> sorting, String severity) {

    log.info("Shacl shapes files: {}.", shaclResources);
    Model shaclModel = JenaUtils.read(shaclResources);

    log.info("Shapes model size: {}", shaclModel.size());

    for (Resource validateResource : validateResources) {
      log.info("Validation of {}", validateResource);

      Model dataModel = getDataModel(validateResource);
      if (dataModel == null) continue;

      log.info("Number of triples to be validated: {}", dataModel.size());
      if (dataModel.isEmpty()) log.error("File did not contain any triples.");

      Model reportModel = validate(dataModel, shaclModel);
      saveReport(validateResource, new Report(reportModel, columns, sorting), destination, outputHtml, severity);
    }
  }

  private Model getDataModel(Resource validateResource) {
    try {
      return JenaUtils.read(validateResource);
    }
    catch (Exception e) {
      log.error("Not an RDF file. Skipping.", e);
      return null;
    }
  }

  private void saveReport(Resource validateResource, Report report, String destination, boolean outputHtml, String severity) {
    if (!shouldOutputReport(report, severity)) {
      log.info("Not reaching severity level. No report is created. Number of results is {}.", report.size());
      return;
    }

    saveRdfReport(validateResource, report.getModel(), destination);
    saveHtmlReport(validateResource, report, destination, outputHtml);
  }

  private void saveHtmlReport(Resource validateResource, Report report, String destination, boolean outputHtml) {
    if (!outputHtml) return;

    File reportFile = getReportFile(validateResource, destination, "html");
    log.info("Creating HTML report '{}'. Report size is {}.", reportFile, report.size());

    try (Writer out = new OutputStreamWriter(new FileOutputStream(reportFile), StandardCharsets.UTF_8)) {
      out.write(thymeleafService.process(validateResource, report));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void saveRdfReport(Resource validateResource, Model reportModel, String destination) {
    File reportFile = getReportFile(validateResource, destination, "ttl");
    log.info("Creating RDF report '{}'. Report size is {}.", reportFile, reportModel.size());
    try (FileOutputStream out = new FileOutputStream(reportFile)) {
      reportModel.write(out, "ttl");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean shouldOutputReport(Report report, String limitSeverity) {
    List<String> severities = getLevelsToCheck(limitSeverity);

    // always output if level is not set
    if (severities.isEmpty()) return true;

    for (String severity : severities) {
      boolean hasMatch = report.getSeverityCount(severity) > 0;
      if (hasMatch) return true;
    }

    return false;
  }

  private List<String> getLevelsToCheck(String severity) {
    if (severity == null) return Collections.emptyList();

    List<String> severities = getSeverities();
    return severities.subList(severities.indexOf(severity), severities.size());
  }

  private File getReportFile(Resource validateResource, String destination, String fileExtension) {
    File root = getDestinationFolder(destination);

    String filename = getFilename(validateResource);
    String reportFileName = StringUtils.substringBeforeLast(filename, ".") + ".report." + fileExtension;
    return new File(root, reportFileName);
  }

  private String getFilename(Resource reportResource) {
    // some special treatment for urls
    if (ClassUtils.isAssignable(reportResource.getClass(), UrlResource.class)) {
      try {
        String path = reportResource.getURL().getPath().substring(1);
        return StringUtils.replace(path, "/", "-");
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    return reportResource.getFilename();
  }

  private File getDestinationFolder(String destination) {
    File root = destination == null ? new File(".")
                                    : new File(destination);

    Preconditions.checkState(root.exists(), "Destination '" + root.getName() + "' not found.");
    Preconditions.checkState(root.isDirectory(), "Destination '" + root.getName() + "' not a directory.");
    return root;
  }

  private Model validate(Model dataModel, Model shapes) {
    Model model = ValidationUtil.validateModel(dataModel, shapes, true).getModel();
    model.setNsPrefix("sh", "http://www.w3.org/ns/shacl#");
    return model;
  }

}
