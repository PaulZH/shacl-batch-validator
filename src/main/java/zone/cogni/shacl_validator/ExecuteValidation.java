package zone.cogni.shacl_validator;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ext.com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.topbraid.shacl.validation.ValidationUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ExecuteValidation implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(ExecuteValidation.class);

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

  private String validate;
  private String shacl;
  private String destination;

  public ExecuteValidation(String validate, String shacl, String destination) {
    this.validate = validate;
    this.shacl = shacl;
    this.destination = destination;
  }


  @Override
  public void run() {
    List<Resource> validateResources = getResources(validate);
    List<Resource> shaclResource = getResources(shacl);

    validate(validateResources, shaclResource);
  }

  private void validate(List<Resource> validateResources, List<Resource> shaclResources) {

    log.info("Shacl shapes files: {}.", shaclResources);
    Model shaclModel = JenaUtils.read(shaclResources);

    for (Resource validateResource : validateResources) {
      log.info("Validation of {}", validateResource);
      Model dataModel = JenaUtils.read(validateResource);
      Model reportModel = validate(dataModel, shaclModel);

      saveReport(validateResource, reportModel);
    }
  }

  private void saveReport(Resource validateResource, Model reportModel) {
    try (FileOutputStream out = new FileOutputStream(getReportFile(validateResource))) {
      reportModel.write(out, "ttl");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private File getReportFile(Resource validateResource) {
    File root = getDestinationFolder();

    String filename = validateResource.getFilename();
    String reportFileName = StringUtils.substringBeforeLast(filename, ".") + ".report.ttl";
    return new File(root, reportFileName);
  }

  private File getDestinationFolder() {
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
