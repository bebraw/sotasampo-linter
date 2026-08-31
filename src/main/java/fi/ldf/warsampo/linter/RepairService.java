package fi.ldf.warsampo.linter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.vocabulary.RDF;

final class RepairService {
    private final RepairOptions options;
    private final RdfLoader loader = new RdfLoader();

    RepairService(RepairOptions options) {
        this.options = options;
    }

    RepairRun run() throws IOException {
        List<Path> files = RdfFiles.discover(options.dataPaths());
        if (files.isEmpty()) {
            throw new IllegalArgumentException("No RDF files found in the selected data paths.");
        }
        if (options.apply()) {
            requireEmptyOutputDirectory(options.outputDirectory());
        }

        List<RepairDefinition> definitions =
                RepairCatalog.load(options.root(), options.repairIds(), options.allAutomatic());
        ProfileValidationContext validation = ProfileValidationContext.load(options.root(), options.profile());
        List<TripleChange> changes = new ArrayList<>();
        Map<Path, Model> sourceModels = new LinkedHashMap<>();
        Set<String> applicableRepairIds = new LinkedHashSet<>();

        for (Path file : files) {
            String module = moduleName(file);
            Model model = loader.load(file);
            List<Finding> beforeFindings = validation.validate(model, module);
            Set<String> beforeViolations = violationSignatures(beforeFindings);

            for (RepairDefinition definition : definitions) {
                List<Triple> matches = matchingTriples(model, definition.badIri());
                if (matches.isEmpty()) {
                    continue;
                }
                requireCorrespondingFinding(definition, beforeFindings, module);
                for (Triple before : matches) {
                    changes.add(new TripleChange(module, definition, before, replace(before, definition)));
                }
                UpdateAction.execute(UpdateFactory.create(definition.update()), model);
                applicableRepairIds.add(definition.id());
                requirePostcondition(model, definition, matches);
            }

            List<Finding> afterFindings = validation.validate(model, module);
            List<Finding> newViolations = afterFindings.stream()
                    .filter(Finding::isViolation)
                    .filter(finding -> !beforeViolations.contains(finding.signature()))
                    .toList();
            if (!newViolations.isEmpty()) {
                throw new IllegalArgumentException(
                        "Repair rejected because it introduces " + newViolations.size() + " violation(s) in " + module);
            }
            sourceModels.put(file, model);
        }

        List<TripleChange> sortedChanges = changes.stream().sorted(TripleChange.ORDER).toList();
        List<RepairDefinition> applicableDefinitions = definitions.stream()
                .filter(definition -> applicableRepairIds.contains(definition.id()))
                .toList();
        String patch = buildPatch(applicableDefinitions);
        UpdateFactory.create(patch);

        List<Path> written = options.apply() ? writeCopies(sourceModels, sortedChanges) : List.of();
        Instant timestamp = Instant.now();
        Model provenance = RepairProvenanceWriter.create(sortedChanges, options.apply(), timestamp);
        return new RepairRun(sortedChanges, patch, provenance, timestamp, options.apply(), written);
    }

    private List<Path> writeCopies(Map<Path, Model> sourceModels, List<TripleChange> changes) throws IOException {
        Files.createDirectories(options.outputDirectory());
        Set<String> changedModules = changes.stream().map(TripleChange::sourceModule).collect(java.util.stream.Collectors.toSet());
        Map<Path, Path> targets = new HashMap<>();
        Set<Path> uniqueTargets = new HashSet<>();
        for (Path source : sourceModels.keySet()) {
            Path target = options.outputDirectory().resolve(relativeOutputPath(source)).normalize();
            if (source.equals(target)) {
                throw new IllegalArgumentException("Repair output would overwrite source data: " + source);
            }
            if (!target.startsWith(options.outputDirectory()) || !uniqueTargets.add(target)) {
                throw new IllegalArgumentException("Cannot derive a unique safe repair output path for " + source);
            }
            targets.put(source, target);
        }

        List<Path> written = new ArrayList<>();
        for (Map.Entry<Path, Model> entry : sourceModels.entrySet()) {
            Path source = entry.getKey();
            Path target = targets.get(source);
            Files.createDirectories(target.getParent());
            if (!changedModules.contains(moduleName(source))) {
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            } else {
                Lang language = RDFLanguages.filenameToLang(source.getFileName().toString());
                try (OutputStream stream = Files.newOutputStream(target)) {
                    RDFDataMgr.write(stream, entry.getValue(), language);
                }
            }
            written.add(target);
        }
        return List.copyOf(written);
    }

    private static List<Triple> matchingTriples(Model model, String badIri) {
        Node bad = NodeFactory.createURI(badIri);
        Node rdfType = RDF.type.asNode();
        List<Triple> matches = new ArrayList<>();
        var iterator = model.getGraph().find(Node.ANY, Node.ANY, Node.ANY);
        while (iterator.hasNext()) {
            Triple triple = iterator.next();
            if (triple.getPredicate().equals(bad)
                    || (triple.getPredicate().equals(rdfType) && triple.getObject().equals(bad))) {
                matches.add(triple);
            }
        }
        return matches.stream().sorted(java.util.Comparator.comparing(TripleChange::format)).toList();
    }

    private static Triple replace(Triple triple, RepairDefinition definition) {
        Node bad = NodeFactory.createURI(definition.badIri());
        Node replacement = NodeFactory.createURI(definition.replacementIri());
        Node predicate = triple.getPredicate().equals(bad) ? replacement : triple.getPredicate();
        Node object = triple.getObject().equals(bad) ? replacement : triple.getObject();
        return Triple.create(triple.getSubject(), predicate, object);
    }

    private static void requirePostcondition(
            Model model, RepairDefinition definition, List<Triple> expectedChanges) {
        List<Triple> remaining = matchingTriples(model, definition.badIri());
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException("Repair failed its postcondition: " + definition.id());
        }
        for (Triple before : expectedChanges) {
            Triple after = replace(before, definition);
            if (!model.getGraph().contains(after)) {
                throw new IllegalArgumentException("Repair did not create its required replacement triple: " + definition.id());
            }
        }
    }

    private static void requireCorrespondingFinding(
            RepairDefinition definition, List<Finding> findings, String module) {
        String rule = "<" + definition.ruleId() + ">";
        String path = "<" + definition.badIri() + ">";
        boolean found = findings.stream()
                .anyMatch(finding -> finding.rule().equals(rule) && finding.path().equals(path));
        if (!found) {
            throw new IllegalArgumentException(
                    "Repair " + definition.id() + " matched data without its required validation finding in " + module);
        }
    }

    private static Set<String> violationSignatures(List<Finding> findings) {
        return findings.stream()
                .filter(Finding::isViolation)
                .map(Finding::signature)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String moduleName(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.startsWith(options.root())) {
            return options.root().relativize(absolute).toString().replace('\\', '/');
        }
        return absolute.toString().replace('\\', '/');
    }

    private Path relativeOutputPath(Path source) {
        Path absolute = source.toAbsolutePath().normalize();
        if (absolute.startsWith(options.root())) {
            return options.root().relativize(absolute);
        }
        return absolute.getFileName();
    }

    private static String buildPatch(List<RepairDefinition> definitions) {
        if (definitions.isEmpty()) {
            return "# No applicable automatic repairs\n";
        }
        List<String> blocks = definitions.stream()
                .map(definition -> "# repair: " + definition.id() + "\n# rule: " + definition.ruleId() + "\n" + definition.update())
                .toList();
        return String.join("\n;\n\n", blocks) + "\n";
    }

    private static void requireEmptyOutputDirectory(Path directory) throws IOException {
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
}
