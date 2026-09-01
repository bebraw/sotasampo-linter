package fi.ldf.warsampo.linter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record ValidationOptions(
        Path root,
        List<Path> dataPaths,
        Profile profile,
        boolean crossModule,
        Path reportPath,
        Path summaryPath,
        Path baselinePath,
        Path writeBaselinePath,
        Path tdbPath) {

    static ValidationOptions parse(String[] args) {
        Path root = Path.of(".").toAbsolutePath().normalize();
        List<Path> dataPaths = new ArrayList<>();
        Profile profile = Profile.WARSAMPO;
        boolean crossModule = false;
        Path reportPath = null;
        Path summaryPath = null;
        Path baselinePath = null;
        Path writeBaselinePath = null;
        Path tdbPath = null;

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            switch (argument) {
                case "--data" -> dataPaths.add(Path.of(requireValue(args, ++index, argument)));
                case "--profile" -> profile = Profile.parse(requireValue(args, ++index, argument));
                case "--cross-module" -> crossModule = true;
                case "--report" -> reportPath = Path.of(requireValue(args, ++index, argument));
                case "--summary" -> summaryPath = Path.of(requireValue(args, ++index, argument));
                case "--baseline" -> baselinePath = Path.of(requireValue(args, ++index, argument));
                case "--write-baseline" -> writeBaselinePath = Path.of(requireValue(args, ++index, argument));
                case "--tdb" -> tdbPath = Path.of(requireValue(args, ++index, argument));
                case "--root" -> root = Path.of(requireValue(args, ++index, argument)).toAbsolutePath().normalize();
                case "--help" -> throw new IllegalArgumentException("Use `warsampo-linter help` for usage.");
                default -> throw new IllegalArgumentException("Unknown validate option: " + argument);
            }
        }

        if (dataPaths.isEmpty()) {
            throw new IllegalArgumentException("At least one --data path is required.");
        }
        if (tdbPath != null && !crossModule) {
            throw new IllegalArgumentException("--tdb requires --cross-module.");
        }

        Path finalRoot = root;
        List<Path> resolvedData = dataPaths.stream().map(path -> resolve(finalRoot, path)).toList();
        return new ValidationOptions(
                root,
                resolvedData,
                profile,
                crossModule,
                resolveNullable(root, reportPath),
                resolveNullable(root, summaryPath),
                resolveNullable(root, baselinePath),
                resolveNullable(root, writeBaselinePath),
                resolveNullable(root, tdbPath));
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " requires a value.");
        }
        return args[index];
    }

    private static Path resolve(Path root, Path path) {
        return path.isAbsolute() ? path.normalize() : root.resolve(path).normalize();
    }

    private static Path resolveNullable(Path root, Path path) {
        return path == null ? null : resolve(root, path);
    }

    void requireSafeOutputPaths(List<Path> dataFiles) {
        Map<String, Path> outputs = new LinkedHashMap<>();
        addOutput(outputs, "report", reportPath);
        addOutput(outputs, "summary", summaryPath);
        addOutput(outputs, "baseline output", writeBaselinePath);

        Map<Path, String> seen = new LinkedHashMap<>();
        for (Map.Entry<String, Path> output : outputs.entrySet()) {
            Path normalized = output.getValue().toAbsolutePath().normalize();
            if (java.nio.file.Files.isDirectory(normalized)) {
                throw new IllegalArgumentException(
                        "Validation output path is an existing directory: " + normalized);
            }
            for (Map.Entry<Path, String> previous : seen.entrySet()) {
                if (pathsConflict(normalized, previous.getKey())) {
                    throw new IllegalArgumentException("Validation output paths conflict: "
                            + previous.getValue() + " and " + output.getKey());
                }
            }
            seen.put(normalized, output.getKey());
            for (Path dataFile : dataFiles) {
                if (pathsConflict(normalized, dataFile)) {
                    throw new IllegalArgumentException(
                            "Validation output conflicts with selected RDF input: " + normalized);
                }
            }
            if (baselinePath != null
                    && pathsConflict(normalized, baselinePath.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException(
                        "Validation output conflicts with the baseline being compared: " + normalized);
            }
        }
    }

    private static void addOutput(Map<String, Path> outputs, String name, Path path) {
        if (path != null) {
            outputs.put(name, path);
        }
    }

    private static boolean pathsConflict(Path first, Path second) {
        return first.equals(second) || first.startsWith(second) || second.startsWith(first);
    }
}
