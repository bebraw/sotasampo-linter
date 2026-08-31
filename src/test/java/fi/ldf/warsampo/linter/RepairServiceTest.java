package fi.ldf.warsampo.linter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.update.UpdateFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepairServiceTest {
    private static final String DASH = "http://datashapes.org/dash#";
    private final Path root = Path.of(".").toAbsolutePath().normalize();
    private final Path fixture = root.resolve("fixtures/repairs/standard-term-typos.ttl");

    @Test
    void dryRunPlansAllFiveGuardedRepairsWithoutChangingSource() throws Exception {
        String original = Files.readString(fixture);
        RepairRun run = new RepairService(options(false, null, Set.of(), true)).run();

        assertFalse(run.applied());
        assertEquals(5, run.changes().size());
        assertEquals(original, Files.readString(fixture));
        assertTrue(run.patch().contains("RepairRdfsSubClassof"));
        assertTrue(run.patch().contains("DELETE"));
        assertTrue(run.patch().contains("WHERE"));
        UpdateFactory.create(run.patch());

        var graphUpdate = ResourceFactory.createResource(DASH + "GraphUpdate");
        assertEquals(5, run.provenance().listResourcesWithProperty(org.apache.jena.vocabulary.RDF.type, graphUpdate).toList().size());
        assertTrue(run.provenance().contains(null, LinterVocabulary.REPAIRS_RULE));
        assertTrue(run.provenance().contains(null, LinterVocabulary.SOURCE_MODULE));
    }

    @Test
    void applyWritesValidatedCopiesAndIsIdempotent(@TempDir Path temporaryDirectory) throws Exception {
        String original = Files.readString(fixture);
        Path output = temporaryDirectory.resolve("repaired");
        RepairOptions applyOptions = options(true, output, Set.of(), true);
        RepairRun applied = new RepairService(applyOptions).run();
        applied.writeOutputs(
                applyOptions,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        Path repaired = output.resolve(root.relativize(fixture));
        assertTrue(Files.isRegularFile(repaired));
        assertEquals(original, Files.readString(fixture));
        assertTrue(Files.isRegularFile(output.resolve(".warsampo-linter/repair.ru")));
        assertTrue(Files.isRegularFile(output.resolve(".warsampo-linter/provenance.ttl")));

        ValidationOptions validationOptions = new ValidationOptions(
                root,
                List.of(repaired),
                Profile.CORE,
                false,
                null,
                null,
                null,
                null,
                null);
        assertTrue(new ValidationService(validationOptions).run().findings().isEmpty());

        RepairRun secondPass = new RepairService(new RepairOptions(
                        root,
                        List.of(repaired),
                        Profile.CORE,
                        Set.of(),
                        true,
                        false,
                        null,
                        null,
                        null))
                .run();
        assertTrue(secondPass.changes().isEmpty());
    }

    @Test
    void localRepairIdSelectsOnlyOneDefinition() throws Exception {
        RepairRun run = new RepairService(options(
                        false,
                        null,
                        Set.of("RepairSkosPreflabel"),
                        false))
                .run();

        assertEquals(1, run.changes().size());
        assertTrue(run.patch().contains("RepairSkosPreflabel"));
        assertFalse(run.patch().contains("RepairOwlSame"));
    }

    @Test
    void applyRefusesNonEmptyDestination(@TempDir Path temporaryDirectory) throws Exception {
        Path output = temporaryDirectory.resolve("repaired");
        Files.createDirectories(output);
        Files.writeString(output.resolve("existing.txt"), "keep");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new RepairService(options(true, output, Set.of(), true)).run());
        assertTrue(error.getMessage().contains("must be empty"));
    }

    private RepairOptions options(boolean apply, Path output, Set<String> ids, boolean allAutomatic) {
        return new RepairOptions(
                root,
                List.of(fixture),
                Profile.CORE,
                ids,
                allAutomatic,
                apply,
                output,
                null,
                null);
    }
}
