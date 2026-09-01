package fi.ldf.warsampo.linter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;

record RepairRun(
        List<TripleChange> changes,
        String patch,
        Model provenance,
        Instant timestamp,
        boolean applied,
        Map<Path, Model> sourceModels) {

    RepairRun {
        changes = List.copyOf(changes);
        sourceModels = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(sourceModels));
    }

    static void preflight(RepairOptions options, List<Path> sources) throws IOException {
        Path customPatch = normalize(options.patchPath());
        Path customProvenance = normalize(options.provenancePath());
        Path mandatoryPatch = normalize(options.mandatoryPatchPath());
        Path mandatoryProvenance = normalize(options.mandatoryProvenancePath());

        if (customPatch != null && customPatch.equals(customProvenance)) {
            throw new IllegalArgumentException("SPARQL Update and provenance paths must differ: " + customPatch);
        }
        if (customPatch != null && customPatch.equals(mandatoryProvenance)) {
            throw new IllegalArgumentException("SPARQL Update path collides with mandatory provenance: " + customPatch);
        }
        if (customProvenance != null && customProvenance.equals(mandatoryPatch)) {
            throw new IllegalArgumentException("Provenance path collides with mandatory SPARQL Update: " + customProvenance);
        }

        Set<Path> normalizedSources = sources.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .collect(java.util.stream.Collectors.toSet());
        for (Path output : java.util.stream.Stream.of(customPatch, customProvenance)
                .filter(java.util.Objects::nonNull)
                .toList()) {
            if (normalizedSources.contains(output)) {
                throw new IllegalArgumentException("Repair metadata would overwrite source data: " + output);
            }
            if (!output.equals(mandatoryPatch) && !output.equals(mandatoryProvenance)) {
                requireAbsent(output, "repair metadata");
            }
        }

        if (options.apply()) {
            requireEmptyDirectory(options.outputDirectory());
            Path outputDirectory = normalize(options.outputDirectory());
            List<Path> plannedFiles = new ArrayList<>(
                    deriveTargets(options, sources, options.outputDirectory()).values());
            plannedFiles.add(mandatoryPatch);
            plannedFiles.add(mandatoryProvenance);
            requireNoPublicationCollision(
                    "SPARQL Update", customPatch, mandatoryPatch, outputDirectory, plannedFiles);
            requireNoPublicationCollision(
                    "provenance", customProvenance, mandatoryProvenance, outputDirectory, plannedFiles);
        }
    }

    void writeOutputs(RepairOptions options, PrintStream out) throws IOException {
        preflight(options, new ArrayList<>(sourceModels.keySet()));

        out.println((applied ? "Applied" : "Planned") + " " + changes.size() + " triple repair(s)");
        out.println("Repair timestamp: " + timestamp);
        if (applied) {
            writeAppliedOutput(options);
            out.println("Source files were copied to " + options.outputDirectory());
            out.println("SPARQL Update: " + options.mandatoryPatchPath());
            out.println("Provenance: " + options.mandatoryProvenancePath());
            writeOptionalMetadataCopies(options, out);
        } else {
            out.println("Source files were not modified (dry run)");
            writeDryRunMetadata(options, out);
        }
    }

    private void writeAppliedOutput(RepairOptions options) throws IOException {
        Path output = options.outputDirectory().toAbsolutePath().normalize();
        Path parent = output.getParent();
        Files.createDirectories(parent);
        Path staging = Files.createTempDirectory(parent, "." + output.getFileName() + ".stage-");
        boolean published = false;
        try {
            writeCopies(options, staging);
            Path metadata = staging.resolve(".warsampo-linter");
            Files.createDirectories(metadata);
            Files.writeString(metadata.resolve("repair.ru"), patch);
            Files.writeString(metadata.resolve("provenance.ttl"), RepairProvenanceWriter.serialize(provenance));

            requireEmptyDirectory(output);
            if (Files.exists(output)) {
                Files.delete(output);
            }
            AtomicFiles.moveNew(staging, output);
            published = true;
        } finally {
            if (!published) {
                deleteTree(staging);
            }
        }
    }

    private void writeCopies(RepairOptions options, Path staging) throws IOException {
        Set<String> changedModules = changes.stream()
                .map(TripleChange::sourceModule)
                .collect(java.util.stream.Collectors.toSet());
        Map<Path, Path> targets = deriveTargets(options, new ArrayList<>(sourceModels.keySet()), staging);
        for (Map.Entry<Path, Model> entry : sourceModels.entrySet()) {
            Path source = entry.getKey();
            Path target = targets.get(source);
            Files.createDirectories(target.getParent());
            if (!changedModules.contains(moduleName(options, source))) {
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            } else {
                Lang language = RDFLanguages.filenameToLang(source.getFileName().toString());
                try (OutputStream stream = Files.newOutputStream(target)) {
                    RDFDataMgr.write(stream, entry.getValue(), language);
                }
            }
        }
    }

    private void writeOptionalMetadataCopies(RepairOptions options, PrintStream out) throws IOException {
        Path mandatoryPatch = normalize(options.mandatoryPatchPath());
        Path mandatoryProvenance = normalize(options.mandatoryProvenancePath());
        Path customPatch = normalize(options.patchPath());
        Path customProvenance = normalize(options.provenancePath());
        if (customPatch != null && !customPatch.equals(mandatoryPatch)) {
            AtomicFiles.writeString(customPatch, patch);
            out.println("Additional SPARQL Update: " + customPatch);
        }
        if (customProvenance != null && !customProvenance.equals(mandatoryProvenance)) {
            AtomicFiles.writeString(customProvenance, RepairProvenanceWriter.serialize(provenance));
            out.println("Additional provenance: " + customProvenance);
        }
    }

    private void writeDryRunMetadata(RepairOptions options, PrintStream out) throws IOException {
        if (options.patchPath() == null) {
            out.println("\n--- SPARQL Update ---");
            out.print(patch);
        } else {
            AtomicFiles.writeString(options.patchPath(), patch);
            out.println("SPARQL Update: " + options.patchPath());
        }

        String provenanceText = RepairProvenanceWriter.serialize(provenance);
        if (options.provenancePath() == null) {
            out.println("\n--- repair provenance (Turtle) ---");
            out.print(provenanceText);
        } else {
            AtomicFiles.writeString(options.provenancePath(), provenanceText);
            out.println("Provenance: " + options.provenancePath());
        }
    }

    private static Map<Path, Path> deriveTargets(RepairOptions options, List<Path> sources, Path destination) {
        Map<Path, Path> targets = new LinkedHashMap<>();
        Set<Path> uniqueTargets = new HashSet<>();
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        for (Path source : sources) {
            Path normalizedSource = source.toAbsolutePath().normalize();
            Path target = normalizedDestination.resolve(relativeOutputPath(options, source)).normalize();
            if (normalizedSource.equals(target)) {
                throw new IllegalArgumentException("Repair output would overwrite source data: " + source);
            }
            if (!target.startsWith(normalizedDestination) || !uniqueTargets.add(target)) {
                throw new IllegalArgumentException("Cannot derive a unique safe repair output path for " + source);
            }
            targets.put(source, target);
        }
        return targets;
    }

    private static String moduleName(RepairOptions options, Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.startsWith(options.root())) {
            return options.root().relativize(absolute).toString().replace('\\', '/');
        }
        return absolute.toString().replace('\\', '/');
    }

    private static Path relativeOutputPath(RepairOptions options, Path source) {
        Path absolute = source.toAbsolutePath().normalize();
        if (absolute.startsWith(options.root())) {
            return options.root().relativize(absolute);
        }
        return absolute.getFileName();
    }

    private static void requireEmptyDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Repair output path is not a directory: " + directory);
        }
        try (var entries = Files.list(directory)) {
            if (entries.findAny().isPresent()) {
                throw new IllegalArgumentException("Repair output directory must be empty: " + directory);
            }
        }
    }

    private static void requireAbsent(Path path, String kind) {
        if (Files.exists(path)) {
            throw new IllegalArgumentException("Refusing to overwrite " + kind + ": " + path);
        }
    }

    private static void requireNoPublicationCollision(
            String kind,
            Path customPath,
            Path sameMandatoryPath,
            Path outputDirectory,
            List<Path> plannedFiles) {
        if (customPath == null || customPath.equals(sameMandatoryPath)) {
            return;
        }
        if (outputDirectory.equals(customPath) || outputDirectory.startsWith(customPath)) {
            throw new IllegalArgumentException(
                    "Custom " + kind + " path conflicts with the repair output directory: " + customPath);
        }
        for (Path plannedFile : plannedFiles) {
            if (pathsConflict(customPath, plannedFile)) {
                throw new IllegalArgumentException(
                        "Custom " + kind + " path conflicts with a published repair file: " + customPath);
            }
        }
    }

    private static boolean pathsConflict(Path first, Path second) {
        return first.equals(second) || first.startsWith(second) || second.startsWith(first);
    }

    private static Path normalize(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
