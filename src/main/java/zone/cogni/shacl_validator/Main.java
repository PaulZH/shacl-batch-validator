package zone.cogni.shacl_validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.List;

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.control.Try.run;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.exit;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToSystemExit"})
@SpringBootApplication
public class Main {

  private static final Logger log = LoggerFactory.getLogger(Main.class);

  private static String usage =
          "usage: java -jar shacl.jar\n" +
                  "                [--shacl folder, file or ant-style file pattern] \n" +
                  "                [--validate folder, file or ant-style file pattern (\"./conceptscheme/**/*.ttl\")] \n" +
                  "optional:       [--destination folder] \n" +
                  "                [--severity outputs only reports of this severity or higher, possible values Info, Warning or Violation] \n";

  private static String shacl;
  private static String validate;
  private static String destination;
  private static String severity;

  public static void main(String[] args) {
    long start = currentTimeMillis();

    processArguments(Arrays.asList(args));

    new ExecuteValidation(validate, shacl, destination, severity).run();

    log.info("Total time {}s.", (currentTimeMillis() - start) / 1000);
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
              Case($("--severity"), () -> severity = value),
              Case($("--help"), () -> run(Main::giveHelp)),
              Case($(), () -> run(() -> invalidArgument(argument)))
      );
    }

    if (destination == null) destination = ".";
    if (validate == null) validate = ".";

    printSettings();

    checkArguments();

  }

  private static void checkArguments() {
    boolean fail = false;

    if (shacl == null) {
      System.out.println("Invalid parameters: --shacl is not set");
      fail = true;
    }

    if (ExecuteValidation.getResources(shacl).isEmpty()) {
      System.out.println("Invalid parameters: --shacl option does not produce results.");
      fail = true;
    }

    if (ExecuteValidation.getResources(validate).isEmpty()) {
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

}
