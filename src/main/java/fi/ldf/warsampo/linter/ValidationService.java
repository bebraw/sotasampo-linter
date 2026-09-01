package fi.ldf.warsampo.linter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.engine.ValidationContext;
import org.apache.jena.shacl.parser.Shape;
import org.apache.jena.shacl.validation.VLib;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.RDF;

final class ValidationService {
    private final ValidationOptions options;
    private final Consumer<String> progress;
    private final RdfLoader loader = new RdfLoader();

    ValidationService(ValidationOptions options) {
        this(options, ignored -> {});
    }

    ValidationService(ValidationOptions options, Consumer<String> progress) {
        this.options = options;
        this.progress = progress;
    }

    ValidationRun run() throws IOException {
        Instant start = Instant.now();
        try (RuntimeMetrics.HeapTracker heap = RuntimeMetrics.trackHeap()) {
            List<Path> dataFiles = RdfFiles.discover(options.dataPaths());
            if (dataFiles.isEmpty()) {
                throw new IllegalArgumentException("No RDF files found in the selected data paths.");
            }
            options.requireSafeOutputPaths(dataFiles);

            Model localShapeModel = loader.loadAll(RdfFiles.discoverRequiredDirectories(
                    options.profile().localShapeDirectories(options.root())));
            Shapes localShapes = Shapes.parse(localShapeModel.getGraph());
            Model unionShapeModel = options.crossModule()
                    ? loader.loadAll(RdfFiles.discoverRequiredDirectories(
                            options.profile().unionShapeDirectories(options.root())))
                    : ModelFactory.createDefaultModel();
            Shapes unionShapes = options.crossModule() ? Shapes.parse(unionShapeModel.getGraph()) : null;
            int ruleCount = countRules(localShapeModel, unionShapeModel);

            Path vocabularyDirectory = options.root().resolve("vocabularies");
            List<Path> vocabularyFiles = RdfFiles.discoverRequiredDirectories(List.of(vocabularyDirectory));
            Model supportModel = loader.loadAll(vocabularyFiles);
            addAuditConfiguration(supportModel);

            Set<String> baseline = Baseline.read(options.baselinePath());
            ValidationAccumulator accumulator = options.crossModule()
                    ? validateUnion(dataFiles, localShapes, unionShapes, supportModel)
                    : validateModules(dataFiles, localShapes, supportModel);

            return ValidationRun.of(
                    accumulator.findings,
                    accumulator.parseFailures,
                    accumulator.modules,
                    accumulator.triples,
                    ruleCount,
                    heap.peakHeapBytes(),
                    Duration.between(start, Instant.now()),
                    accumulator.unionRuleTimings,
                    baseline);
        }
    }

    private ValidationAccumulator validateModules(List<Path> files, Shapes shapes, Model supportModel) {
        ValidationAccumulator accumulator = new ValidationAccumulator();
        for (Path file : files) {
            String module = moduleName(file);
            Model data;
            try {
                data = loader.load(file);
            } catch (RuntimeException exception) {
                accumulator.parseFailures.add(module + ": " + rootMessage(exception));
                continue;
            }
            long sourceTriples = data.size();
            collect(shapes, ModelFactory.createUnion(data, supportModel), module, accumulator);
            accumulator.modules++;
            accumulator.triples += sourceTriples;
        }
        return accumulator;
    }

    private ValidationAccumulator validateUnion(
            List<Path> files, Shapes localShapes, Shapes unionShapes, Model supportModel) throws IOException {
        boolean temporary = options.tdbPath() == null;
        Path tdbPath = temporary ? Files.createTempDirectory("warsampo-linter-tdb-") : options.tdbPath();
        if (!temporary) {
            requireEmptyDirectory(tdbPath);
        }

        Dataset dataset = TDB2Factory.connectDataset(tdbPath.toString());
        ValidationAccumulator accumulator = new ValidationAccumulator();
        try {
            for (Path file : files) {
                String module = moduleName(file);
                Model data;
                try {
                    data = loader.load(file);
                } catch (RuntimeException exception) {
                    accumulator.parseFailures.add(module + ": " + rootMessage(exception));
                    continue;
                }

                long sourceTriples = data.size();
                collect(localShapes, ModelFactory.createUnion(data, supportModel), module, accumulator);
                dataset.begin(ReadWrite.WRITE);
                try {
                    dataset.getDefaultModel().add(data);
                    dataset.commit();
                } catch (RuntimeException exception) {
                    dataset.abort();
                    throw exception;
                } finally {
                    dataset.end();
                }
                accumulator.triples += sourceTriples;
                accumulator.modules++;
            }

            if (accumulator.parseFailures.isEmpty()) {
                dataset.begin(ReadWrite.WRITE);
                try {
                    dataset.getDefaultModel().add(supportModel);
                    dataset.commit();
                } finally {
                    dataset.end();
                }
                dataset.begin(ReadWrite.READ);
                try {
                    Set<String> localKeys = accumulator.findings.stream()
                            .map(Finding::semanticKey)
                            .collect(java.util.stream.Collectors.toSet());
                    ValidationAccumulator unionAccumulator = new ValidationAccumulator();
                    collectUnion(unionShapes, dataset.getDefaultModel(), unionAccumulator);
                    unionAccumulator.findings.stream()
                            .filter(finding -> !localKeys.contains(finding.semanticKey()))
                            .forEach(accumulator.findings::add);
                    accumulator.unionRuleTimings.addAll(unionAccumulator.unionRuleTimings);
                } finally {
                    dataset.end();
                }
            }
        } finally {
            dataset.close();
            if (temporary) {
                deleteTree(tdbPath);
            }
        }
        return accumulator;
    }

    private void collect(Shapes shapes, Model data, String module, ValidationAccumulator accumulator) {
        ValidationReport report = ShaclValidator.get().validate(shapes, data.getGraph());
        collectReport(report, data, module, accumulator);
    }

    private void collectUnion(Shapes shapes, Model data, ValidationAccumulator accumulator) {
        ValidationContext context = ValidationContext.create(shapes, data.getGraph());
        List<Shape> targetShapes = shapes.getTargetShapes().stream()
                .sorted(Comparator.comparing(shape -> shape.getShapeNode().toString()))
                .toList();
        for (Shape shape : targetShapes) {
            String rule = formatNode(shape.getShapeNode());
            progress.accept("Union rule " + rule + " started");
            Instant start = Instant.now();
            Collection<Node> focusNodes = VLib.focusNodes(data.getGraph(), shape);
            progress.accept("Union rule " + rule + " selected " + focusNodes.size() + " focus node(s)");
            for (Node focusNode : focusNodes) {
                VLib.validateShape(context, data.getGraph(), shape, focusNode);
            }
            Duration duration = Duration.between(start, Instant.now());
            accumulator.unionRuleTimings.add(new UnionRuleTiming(rule, focusNodes.size(), duration));
            progress.accept("Union rule " + rule + " completed in " + duration.toMillis() + " ms");
        }
        collectReport(context.generateReport(), data, "@union", accumulator);
    }

    private void collectReport(
            ValidationReport report, Model data, String module, ValidationAccumulator accumulator) {
        Model reportModel = ModelFactory.createModelForGraph(report.getGraph());
        List<Resource> resources = reportModel.listResourcesWithProperty(RDF.type, Finding.VALIDATION_RESULT).toList();
        List<Finding> findings = resources.stream()
                .map(resource -> Finding.from(resource, reportModel, data, module))
                .toList();
        accumulator.findings.addAll(Finding.normalizeOccurrences(findings));
    }

    private static String formatNode(Node node) {
        return node.isURI() ? "<" + node.getURI() + ">" : node.toString();
    }

    private void addAuditConfiguration(Model model) {
        model.add(LinterVocabulary.AUDIT_TARGET, RDF.type, LinterVocabulary.AUDIT_TARGET_CLASS);
        model.add(
                LinterVocabulary.AUDIT_TARGET,
                LinterVocabulary.INTERNAL_NAMESPACE,
                "http://ldf.fi/warsa/");
    }

    private String moduleName(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.startsWith(options.root())) {
            return options.root().relativize(absolute).toString().replace('\\', '/');
        }
        return absolute.toString().replace('\\', '/');
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static int countRules(Model... shapeModels) {
        return (int) java.util.Arrays.stream(shapeModels)
                .flatMap(shapeModel -> shapeModel
                        .listSubjectsWithProperty(
                                org.apache.jena.rdf.model.ResourceFactory.createProperty(Finding.SH + "severity"))
                        .toList()
                        .stream())
                .filter(Resource::isURIResource)
                .distinct()
                .count();
    }

    private static void requireEmptyDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            if (!Files.isDirectory(directory)) {
                throw new IllegalArgumentException("TDB path is not a directory: " + directory);
            }
            try (var entries = Files.list(directory)) {
                if (entries.findAny().isPresent()) {
                    throw new IllegalArgumentException("Caller-owned TDB directory must be empty: " + directory);
                }
            }
        } else {
            Files.createDirectories(directory);
        }
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

    private static final class ValidationAccumulator {
        private final List<Finding> findings = new ArrayList<>();
        private final List<String> parseFailures = new ArrayList<>();
        private final List<UnionRuleTiming> unionRuleTimings = new ArrayList<>();
        private int modules;
        private long triples;
    }
}
