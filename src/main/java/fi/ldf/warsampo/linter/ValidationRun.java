package fi.ldf.warsampo.linter;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Set;

record ValidationRun(
        List<Finding> findings,
        List<Finding> regressions,
        List<String> parseFailures,
        int modules,
        long triples,
        int rules,
        long peakHeapBytes,
        Duration duration) {

    boolean hasRegressions() {
        return !regressions.isEmpty();
    }

    void writeOutputs(ValidationOptions options, PrintStream out) throws IOException {
        List<String> summary = summaryLines();
        summary.forEach(out::println);

        if (options.summaryPath() != null) {
            Baseline.ensureParent(options.summaryPath());
            Files.write(options.summaryPath(), summary, StandardCharsets.UTF_8);
        }
        if (options.reportPath() != null) {
            ShaclReportWriter.write(options.reportPath(), findings);
        }
        if (options.writeBaselinePath() != null) {
            Baseline.write(options.writeBaselinePath(), findings);
        }
    }

    private List<String> summaryLines() {
        long violations = findings.stream().filter(Finding::isViolation).count();
        long warnings = findings.stream().filter(Finding::isWarning).count();
        long info = findings.size() - violations - warnings;
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Validated " + modules + " module(s), " + triples + " triples in " + duration.toMillis() + " ms");
        lines.add("Rules: " + rules + "; peak JVM heap: " + formatMebibytes(peakHeapBytes) + " MiB");
        lines.add("Findings: " + violations + " violation(s), " + warnings + " warning(s), " + info + " info");
        lines.add("New violations: " + regressions.size());
        if (!parseFailures.isEmpty()) {
            lines.add("Parse failures: " + parseFailures.size());
        }
        regressions.stream().limit(20).forEach(finding -> lines.add(
                "VIOLATION " + finding.rule() + " " + finding.sourceModule() + " " + finding.message()));
        if (regressions.size() > 20) {
            lines.add("... and " + (regressions.size() - 20) + " more new violation(s)");
        }
        return List.copyOf(lines);
    }

    static ValidationRun of(
            List<Finding> findings,
            List<String> parseFailures,
            int modules,
            long triples,
            int rules,
            long peakHeapBytes,
            Duration duration,
            Set<String> baseline) {
        List<Finding> sorted = findings.stream().sorted(Finding.ORDER).toList();
        List<Finding> regressions = sorted.stream()
                .filter(Finding::isViolation)
                .filter(finding -> !baseline.contains(finding.signature()))
                .toList();
        return new ValidationRun(
                sorted,
                regressions,
                List.copyOf(parseFailures),
                modules,
                triples,
                rules,
                peakHeapBytes,
                duration);
    }

    private static String formatMebibytes(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f", bytes / 1024.0 / 1024.0);
    }
}
