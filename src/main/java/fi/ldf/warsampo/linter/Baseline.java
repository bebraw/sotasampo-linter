package fi.ldf.warsampo.linter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Baseline {
    private static final String HEADER = "# warsampo-linter baseline v1";

    private Baseline() {}

    static Set<String> read(Path path) throws IOException {
        if (path == null) {
            return Set.of();
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Baseline does not exist: " + path);
        }
        Set<String> signatures = new HashSet<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            signatures.add(line.split("\\t", 2)[0]);
        }
        return Set.copyOf(signatures);
    }

    static void write(Path path, List<Finding> findings) throws IOException {
        ensureParent(path);
        List<String> lines = new java.util.ArrayList<>();
        lines.add(HEADER);
        lines.add("# signature\tseverity\trule\tfocus\tpath\tvalue\tsource\tmessage");
        findings.stream().sorted(Finding.ORDER).forEach(finding -> lines.add(String.join("\t",
                finding.signature(),
                escape(finding.severity()),
                escape(finding.rule()),
                escape(finding.focus()),
                escape(finding.path()),
                escape(finding.value()),
                escape(finding.sourceModule()),
                escape(finding.message()))));
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    static void ensureParent(Path path) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }
}
