package fi.ldf.warsampo.linter;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || "--help".equals(args[0]) || "help".equals(args[0])) {
            printUsage(out);
            return 0;
        }

        try {
            return switch (args[0]) {
                case "validate" -> runValidate(slice(args), out, err);
                case "repair" -> runRepair(slice(args), out, err);
                default -> {
                    err.printf("Unknown command: %s%n%n", args[0]);
                    printUsage(err);
                    yield 2;
                }
            };
        } catch (IllegalArgumentException exception) {
            err.println("Error: " + exception.getMessage());
            return 2;
        } catch (Exception exception) {
            err.println("Command failed: " + exception.getMessage());
            exception.printStackTrace(err);
            return 2;
        }
    }

    private static int runValidate(String[] args, PrintStream out, PrintStream err) throws Exception {
        ValidationOptions options = ValidationOptions.parse(args);
        ValidationRun run = new ValidationService(options, err::println).run();
        run.writeOutputs(options, out);

        if (!run.parseFailures().isEmpty()) {
            run.parseFailures().forEach(failure -> err.println("Parse failure: " + failure));
            return 2;
        }
        return run.hasRegressions() ? 1 : 0;
    }

    private static int runRepair(String[] args, PrintStream out, PrintStream err) throws Exception {
        RepairOptions options = RepairOptions.parse(args);
        RepairRun run = new RepairService(options).run();
        run.writeOutputs(options, out);
        return 0;
    }

    private static String[] slice(String[] args) {
        String[] result = new String[args.length - 1];
        System.arraycopy(args, 1, result, 0, result.length);
        return result;
    }

    static void printUsage(PrintStream out) {
        out.println("""
                WarSampo linked-data linter

                Usage:
                  warsampo-linter validate --data PATH [--data PATH ...] [options]
                  warsampo-linter repair --data PATH [options]

                Validate options:
                  --profile NAME          core, skos, or warsampo (default: warsampo)
                  --cross-module          Validate a complete disk-backed union graph
                  --report PATH           Write a complete aggregate SHACL report as Turtle
                  --summary PATH          Write the stable human-readable summary
                  --baseline PATH         Compare violations with a committed baseline
                  --write-baseline PATH   Write complete current violations as a baseline
                  --tdb PATH              Reuse an empty caller-owned TDB2 directory
                  --root PATH             Repository root (default: current directory)

                Repair options:
                  --profile NAME          Minimum guard: core, skos, or warsampo (default: core)
                  --repair ID             Select a repair by IRI or local ID (repeatable)
                  --all-automatic         Select all automatic repairs (default)
                  --dry-run               Print/write a patch without changing data (default)
                  --apply                 Apply to new copies; requires --output-dir
                  --output-dir PATH       Empty destination for repaired data copies
                  --patch PATH            Write the guarded SPARQL Update to a new file
                  --provenance PATH       Write repair provenance as Turtle to a new file
                  --root PATH             Repository root (default: current directory)
                """);
    }
}
