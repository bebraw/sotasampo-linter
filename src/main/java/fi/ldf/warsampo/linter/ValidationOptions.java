package fi.ldf.warsampo.linter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
}
