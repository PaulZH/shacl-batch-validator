package zone.cogni.shacl_validator;

import io.vavr.control.Try;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.exit;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToSystemExit"})
@SpringBootApplication
public class Main implements CommandLineRunner {

  /*
  SH.sourceShape,
                                                                    SH.resultPath,
                                                                    SH.sourceConstraintComponent,
                                                                    SH.resultMessage
   */
  private static final Logger log = LoggerFactory.getLogger(Main.class);

  private static String COLUMNS_DEFAULT = "resultSeverity,sourceShape,resultPath,resultMessage,focusNode,value";
  private static String SORTING_DEFAULT = "sourceShape,resultPath,sourceConstraintComponent,resultMessage";

  private static String usage =
          "usage: java -jar shacl.jar\n" +
                  "                [--shacl folder, file or ant-style file pattern] \n" +
                  "                [--validate folder, file or ant-style file pattern (\"./conceptscheme/**/*.ttl\")] \n" +
                  "optional:       [--destination folder] \n" +
                  "                [--html true (default false)] \n" +
                  "                [--columns comma separated list of html report columns \n" +
                  "                           possible values are: detail, focusNode, resultMessage, resultPath, \n" +
                  "                                                resultSeverity, sourceConstraint, sourceShape, \n" +
                  "                                                sourceConstraintComponent and value\n" +
                  "                           default value: " + COLUMNS_DEFAULT + "\n" +
                  "                [--sorting comma separated list of html report sort columns\n" +
                  "                           possible values are: detail, focusNode, resultMessage, resultPath, \n" +
                  "                                                resultSeverity, sourceConstraint, sourceShape, \n" +
                  "                                                sourceConstraintComponent and value \n" +
                  "                           default value: " + SORTING_DEFAULT + " \n" +
                  "                [--severity outputs only reports of this severity or higher, possible values Info, Warning or Violation] \n";

  private static String shacl;
  private static String validate;
  private static String destination;
  private static String severity;
  private static String html;
  private static String columns;
  private static String sorting;

  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }

  private static void processArguments(List<String> arguments) {
    if (arguments.isEmpty() || arguments.get(0).equals("--help")) {
      System.out.println(usage);
      exit(0);
    }

    for (int i = 0; i < arguments.size() - 1; i += 2) {
      String argument = arguments.get(i);
      String value = arguments.get(i + 1);

      Match(argument).of(
              Case($("--shacl"), () -> shacl = value),
              Case($("--validate"), () -> validate = value),
              Case($("--destination"), () -> destination = value),
              Case($("--html"), () -> html = value),
              Case($("--columns"), () -> columns = value),
              Case($("--sorting"), () -> sorting = value),
              Case($("--severity"), () -> severity = value),
              Case($("--help"), () -> Try.run(Main::giveHelp)),
              Case($(), () -> Try.run(() -> invalidArgument(argument)))
      );
    }

    if (destination == null) destination = ".";
    if (validate == null) validate = ".";
    if (html == null) html = "false";

    if (Objects.equals(html, "true")) {
      if (columns == null) columns = COLUMNS_DEFAULT;
      if (sorting == null) sorting = SORTING_DEFAULT;
    }

    printSettings();

    checkArguments();
  }

  private static void checkArguments() {
    boolean fail = false;

    if (shacl == null) {
      System.out.println("Invalid parameters: --shacl is not set");
      fail = true;
    }

    if (ValidationService.getResources(shacl).isEmpty()) {
      System.out.println("Invalid parameters: --shacl option does not produce results.");
      fail = true;
    }

    if (ValidationService.getResources(validate).isEmpty()) {
      System.out.println("Invalid parameters: --validate option does not produce results.");
      fail = true;
    }

    if (severity != null && !Arrays.asList("Info", "Warning", "Violation").contains(severity)) {
      System.out.println("Invalid parameters: --severity expects one of Info, Warning or Violation.");
      fail = true;
    }

    if (fail) giveHelp();
  }

  private static void printSettings() {
    System.out.println("");
    System.out.println("");
    System.out.println("Running with settings: ");
    System.out.println("");
    System.out.println("\t\t Shacl           : " + shacl);
    System.out.println("\t\t Validate        : " + validate);
    System.out.println("\t\t Destination     : " + destination);
    System.out.println("\t\t Html            : " + html);
    System.out.println("\t\t Columns         : " + columns);
    System.out.println("\t\t Sorting         : " + sorting);
    System.out.println("\t\t Severity        : " + (severity == null ? "not set, outputting conforming and non-conforming shacl reports"
                                                                     : severity));
    System.out.println("");
  }

  private static void invalidArgument(String argument) {
    String message = "\ninvalid argument";
    if (!argument.contains("--")) {
      message += ", if your OS accidentally expanded the arguments surround the argument with quotes";
    }
    message += " '" + argument + "'";

    System.err.println(message);
    giveHelp();
  }

  private static void giveHelp() {
    System.out.println("\n" + usage);
    exit(1);
  }

  private ValidationService validationService;

  public Main(ValidationService validationService) {
    this.validationService = validationService;
  }

  public void run(String... args) throws Exception {
    long start = currentTimeMillis();

    processArguments(Arrays.asList(args));

    validationService.run(validate, shacl, destination, Boolean.parseBoolean(html), getList(columns), getList(sorting), severity);

    log.info("Total time {}s.", (currentTimeMillis() - start) / 1000);
  }

  private List<String> getList(String toBeSplit) {
    if (StringUtils.isBlank(toBeSplit)) return Collections.emptyList();

    return Arrays.asList(toBeSplit.split(","));
  }
}
