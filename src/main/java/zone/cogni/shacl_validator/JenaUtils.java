package zone.cogni.shacl_validator;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.ext.com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFErrorHandler;
import org.apache.jena.rdf.model.RDFReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class JenaUtils {

  private static final Logger log = LoggerFactory.getLogger(JenaUtils.class);

  public static Model read(Resource... resources) {
    return read(Arrays.asList(resources));
  }

  public static Model read(Iterable<Resource> resources) {
    return read(resources, null);
  }

  public static Model read(Iterable<Resource> resources, Map<String, Object> readerProperties) {
    Model model = ModelFactory.createDefaultModel();

    for (Resource resource : resources) {
      InputStream inputstream = null;
      try {
        inputstream = resource.getInputStream();
        InternalRdfErrorHandler errorHandler = new InternalRdfErrorHandler(resource.getDescription());

        RDFReader rdfReader = getReader(model, resource, errorHandler, readerProperties);
        rdfReader.read(model, inputstream, null);

        Preconditions.checkState(!errorHandler.isFailure(), errorHandler.getInfo());
      }
      catch (IOException e) {
        closeQuietly(model);

        throw new RuntimeException(e);
      }
      catch (RuntimeException e) {
        closeQuietly(model);

        throw e;
      }
      finally {
        IOUtils.closeQuietly(inputstream);
      }
    }

    return model;
  }

  private static void closeQuietly(Model... models) {
    Arrays.stream(models).filter(Objects::nonNull).filter(model -> !model.isClosed()).forEach(model -> {
      try {
        model.close();
      }
      catch (Exception e) {
        log.warn("Closing model failed.", e);
      }
    });
  }

  private static RDFReader getReader(Model model, Resource resource, RDFErrorHandler rdfErrorHandler, Map<String, Object> readerProperties) {
    return getReader(model, rdfErrorHandler, readerProperties, getRdfSyntax(resource));
  }

  private static RDFReader getReader(Model model, RDFErrorHandler rdfErrorHandler, Map<String, Object> readerProperties, String language) {
    RDFReader rdfReader = getReaderByRdfSyntax(model, language);
    rdfReader.setErrorHandler(rdfErrorHandler);
    if (readerProperties == null) return rdfReader;

    for (Map.Entry<String, Object> entry : readerProperties.entrySet()) {
      rdfReader.setProperty(entry.getKey(), entry.getValue());
    }

    return rdfReader;
  }

  private static String getRdfSyntax(Resource resource) {
    String extension = StringUtils.lowerCase(StringUtils.substringAfterLast(resource.getFilename(), "."));
    if ("owl".equals(extension)) {
      return null;
    }
    if ("nt".equals(extension)) {
      return "N-TRIPLE";
    }
    if ("n3".equals(extension) || "ttl".equals(extension)) {
      return "TURTLE";
    }
    return null;
  }

  private static RDFReader getReaderByRdfSyntax(Model model, String language) {
    try {
      return model.getReader(language);
    }
    catch (IllegalStateException ignored) {
      return model.getReader();
    }
  }

  private static class InternalRdfErrorHandler implements RDFErrorHandler {

    private final String info;
    private boolean failure;

    private InternalRdfErrorHandler(String loadedFile) {
      info = "Load rdf file (" + loadedFile + ") problem.";
    }

    private boolean isFailure() {
      return failure;
    }

    private String getInfo() {
      return info;
    }

    @Override
    public void warning(Exception e) {
      String message = e.getMessage();
      if (null != message && message.contains("ISO-639 does not define language:")) {
        log.warn("{}: {}", info, message);
        return;
      }
      log.warn(info, e);
    }

    @Override
    public void error(Exception e) {
      failure = true;
      log.error(info, e);
    }

    @Override
    public void fatalError(Exception e) {
      failure = true;
      log.error(info, e);
    }
  }
}
