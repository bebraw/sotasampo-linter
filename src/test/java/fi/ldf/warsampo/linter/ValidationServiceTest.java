package fi.ldf.warsampo.linter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationServiceTest {
    private final Path root = Path.of(".").toAbsolutePath().normalize();

    @Test
    void validCoreFixtureConforms() throws Exception {
        ValidationRun run = validate("fixtures/positive/core-valid.ttl", false, null, null);

        assertTrue(run.parseFailures().isEmpty());
        assertTrue(run.findings().isEmpty());
        assertFalse(run.hasRegressions());
    }

    @Test
    void invalidCoreFixtureFindsVocabularyError() throws Exception {
        ValidationRun run = validate("fixtures/negative/core-invalid.ttl", false, null, null);

        assertTrue(run.parseFailures().isEmpty());
        assertEquals(1, run.findings().size());
        assertTrue(run.findings().stream().anyMatch(finding -> finding.rule().contains("KnownVocabularyTermShape")));
        assertTrue(run.hasRegressions());
    }

    @Test
    void strictParserRejectsIllTypedLiteral() throws Exception {
        ValidationRun run = validate("fixtures/negative/ill-typed.ttl", false, null, null);

        assertEquals(1, run.parseFailures().size());
        assertTrue(run.findings().isEmpty());
        assertFalse(run.hasRegressions());
    }

    @Test
    void committedBaselineSuppressesKnownViolations(@TempDir Path temporaryDirectory) throws Exception {
        Path baseline = temporaryDirectory.resolve("baseline.tsv");
        ValidationRun first = validate("fixtures/negative/core-invalid.ttl", false, null, baseline);
        assertTrue(first.hasRegressions());

        ValidationRun second = validate("fixtures/negative/core-invalid.ttl", false, baseline, null);
        assertFalse(second.hasRegressions());
        assertEquals(1, second.findings().size());
    }

    @Test
    void reportIsDeterministicAndValidTurtle(@TempDir Path temporaryDirectory) throws Exception {
        ValidationRun run = validate("fixtures/negative/core-invalid.ttl", false, null, null);
        Path first = temporaryDirectory.resolve("first.ttl");
        Path second = temporaryDirectory.resolve("second.ttl");

        ShaclReportWriter.write(first, run.findings());
        ShaclReportWriter.write(second, run.findings());

        assertEquals(Files.readString(first), Files.readString(second));
        assertFalse(new RdfLoader().load(first).isEmpty());
    }

    @Test
    void crossModuleModeUsesDiskBackedUnion(@TempDir Path temporaryDirectory) throws Exception {
        ValidationRun run = validate(
                "fixtures/positive/core-valid.ttl",
                true,
                null,
                null,
                temporaryDirectory.resolve("tdb"));

        assertTrue(run.parseFailures().isEmpty());
        assertEquals(1, run.modules());
        assertFalse(run.hasRegressions());
    }

    private ValidationRun validate(String data, boolean crossModule, Path baseline, Path writeBaseline)
            throws Exception {
        return validate(data, crossModule, baseline, writeBaseline, null);
    }

    private ValidationRun validate(
            String data,
            boolean crossModule,
            Path baseline,
            Path writeBaseline,
            Path tdb)
            throws Exception {
        ValidationOptions options = new ValidationOptions(
                root,
                List.of(root.resolve(data)),
                Profile.CORE,
                crossModule,
                null,
                null,
                baseline,
                writeBaseline,
                tdb);
        ValidationRun run = new ValidationService(options).run();
        if (writeBaseline != null) {
            Baseline.write(writeBaseline, run.findings());
        }
        return run;
    }
}
