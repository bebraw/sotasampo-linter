package fi.ldf.warsampo.linter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

record RepairOptions(
        Path root,
        List<Path> dataPaths,
        Profile profile,
        Set<String> repairIds,
        boolean allAutomatic,
        boolean apply,
        Path outputDirectory,
        Path patchPath,
        Path provenancePath) {

    static RepairOptions parse(String[] args) {
        Path root = Path.of(".").toAbsolutePath().normalize();
        List<Path> dataPaths = new ArrayList<>();
        Profile profile = Profile.CORE;
        Set<String> repairIds = new LinkedHashSet<>();
        boolean allAutomatic = false;
        boolean apply = false;
        boolean dryRun = false;
        Path outputDirectory = null;
        Path patchPath = null;
        Path provenancePath = null;

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            switch (argument) {
                case "--data" -> dataPaths.add(Path.of(requireValue(args, ++index, argument)));
                case "--profile" -> profile = Profile.parse(requireValue(args, ++index, argument));
                case "--repair" -> repairIds.add(requireValue(args, ++index, argument));
                case "--all-automatic" -> allAutomatic = true;
                case "--apply" -> apply = true;
                case "--dry-run" -> dryRun = true;
                case "--output-dir" -> outputDirectory = Path.of(requireValue(args, ++index, argument));
                case "--patch" -> patchPath = Path.of(requireValue(args, ++index, argument));
                case "--provenance" -> provenancePath = Path.of(requireValue(args, ++index, argument));
                case "--root" -> root = Path.of(requireValue(args, ++index, argument)).toAbsolutePath().normalize();
                case "--help" -> throw new IllegalArgumentException("Use `warsampo-linter help` for usage.");
                default -> throw new IllegalArgumentException("Unknown repair option: " + argument);
            }
        }

        if (dataPaths.isEmpty()) {
            throw new IllegalArgumentException("At least one --data path is required.");
        }
        if (allAutomatic && !repairIds.isEmpty()) {
            throw new IllegalArgumentException("Use either --all-automatic or --repair, not both.");
        }
        if (apply && dryRun) {
            throw new IllegalArgumentException("Use either --apply or --dry-run, not both.");
        }
        if (apply && outputDirectory == null) {
            throw new IllegalArgumentException("--apply requires --output-dir; source data is never overwritten.");
        }
        if (!apply && outputDirectory != null) {
            throw new IllegalArgumentException("--output-dir is only valid with --apply.");
        }
        if (!allAutomatic && repairIds.isEmpty()) {
            allAutomatic = true;
        }

        Path finalRoot = root;
        List<Path> resolvedData = dataPaths.stream().map(path -> resolve(finalRoot, path)).toList();
        return new RepairOptions(
                root,
                resolvedData,
                profile,
                Set.copyOf(repairIds),
                allAutomatic,
                apply,
                resolveNullable(root, outputDirectory),
                resolveNullable(root, patchPath),
                resolveNullable(root, provenancePath));
    }

    Path mandatoryPatchPath() {
        return apply ? outputDirectory.resolve(".warsampo-linter/repair.ru") : null;
    }

    Path mandatoryProvenancePath() {
        return apply ? outputDirectory.resolve(".warsampo-linter/provenance.ttl") : null;
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
