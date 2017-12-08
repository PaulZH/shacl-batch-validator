package zone.cogni.shacl_validator;

import org.apache.jena.rdf.model.Model;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class ThymeleafService {

  private final TemplateEngine templateEngine;

  public ThymeleafService(TemplateEngine templateEngine) {
    this.templateEngine = templateEngine;
  }

  public String process(Resource validateResource, Model reportModel) {
    Context context = new Context();
    context.setVariable("name", validateResource.getFilename());
    context.setVariable("report", new Report(reportModel));

    return templateEngine.process("report", context);
  }

}