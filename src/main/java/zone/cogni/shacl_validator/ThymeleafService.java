package zone.cogni.shacl_validator;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class ThymeleafService {

  private final TemplateEngine templateEngine;

  public ThymeleafService(TemplateEngine templateEngine) {
    this.templateEngine = templateEngine;
  }

  public String process () {
    Context context = new Context();

    return templateEngine.process("report", context);
  }
}
