package fi.ldf.warsampo.linter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.RDF;

final class ValidationService {
    private final ValidationOptions options;
    private final RdfLoader loader = new RdfLoader();

    ValidationService(ValidationOptions options) {
        this.options = options;
    }

    ValidationRun run() throws IOException {
        Instant start = Instant.now();
        List<Path> dataFiles = RdfFiles.discover(options.dataPaths());
        if (dataFiles.isEmpty()) {
            throw new IllegalArgumentException("No RDF files found in the selected data paths.");
        }

        List<Path> shapeFiles = RdfFiles.discoverRequiredDirectories(
                options.profile().shapeDirectories(options.root(), options.crossModule()));
        Model shapeModel = loader.loadAll(shapeFiles);
        Shapes shapes = Shapes.parse(shapeModel.getGraph());

        Path vocabularyDirectory = options.root().resolve("vocabularies");
        List<Path> vocabularyFiles = RdfFiles.discoverRequiredDirectories(List.of(vocabularyDirectory));
        Model supportModel = loader.loadAll(vocabularyFiles);
        addAuditConfiguration(supportModel);

        Set<String> baseline = Baseline.read(options.baselinePath());
        ValidationAccumulator accumulator = options.crossModule()
                ? validateUnion(dataFiles, shapes, supportModel)
                : validateModules(dataFiles, shapes, supportModel);

        return ValidationRun.of(
                accumulator.findings,
                accumulator.parseFailures,
                accumulator.modules,
                accumulator.triples,
                Duration.between(start, Instant.now()),
                baseline);
    }

    private ValidationAccumulator validateModules(List<Path> files, Shapes shapes, Model supportModel) {
        ValidationAccumulator accumulator = new ValidationAccumulator();
        for (Path file : files) {
            String module = moduleName(file);
            try {
                Model data = loader.load(file);
                long sourceTriples = data.size();
                data.add(supportModel);
                collect(shapes, data, module, accumulator);
                accumulator.modules++;
                accumulator.triples += sourceTriples;
            } catch (RuntimeException exception) {
                accumulator.parseFailures.add(module + ": " + rootMessage(exception));
            }
        }
        return accumulator;
    }

    private ValidationAccumulator validateUnion(List<Path> files, Shapes shapes, Model supportModel) throws IOException {
        boolean temporary = options.tdbPath() == null;
        Path tdbPath = temporary ? Files.createTempDirectory("warsampo-linter-tdb-") : options.tdbPath();
        if (!temporary) {
            requireEmptyDirectory(tdbPath);
        }

        Dataset dataset = TDB2Factory.connectDataset(tdbPath.toString());
        ValidationAccumulator accumulator = new ValidationAccumulator();
        try {
            for (Path file : files) {
                dataset.begin(ReadWrite.WRITE);
                try {
                    long before = dataset.getDefaultModel().size();
                    loader.parseInto(dataset.getDefaultModel(), file);
                    long after = dataset.getDefaultModel().size();
                    accumulator.triples += after - before;
                    accumulator.modules++;
                    dataset.commit();
                } catch (RuntimeException exception) {
                    dataset.abort();
                    accumulator.parseFailures.add(moduleName(file) + ": " + rootMessage(exception));
                } finally {
                    dataset.end();
                }
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
                    collect(shapes, dataset.getDefaultModel(), "@union", accumulator);
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
        Model reportModel = ModelFactory.createModelForGraph(report.getGraph());
        List<Resource> resources = reportModel.listResourcesWithProperty(RDF.type, Finding.VALIDATION_RESULT).toList();
        resources.stream()
                .map(resource -> Finding.from(resource, reportModel, module))
                .forEach(accumulator.findings::add);
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
        private int modules;
        private long triples;
    }
}
