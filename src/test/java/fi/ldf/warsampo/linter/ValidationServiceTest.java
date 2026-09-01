package fi.ldf.warsampo.linter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationServiceTest {
    private final Path root = Path.of(".").toAbsolutePath().normalize();

    @Test
    void executableRulesSatisfyTheMetadataContract() throws Exception {
        var model = new RdfLoader().loadAll(RdfFiles.discoverRequiredDirectories(List.of(
                root.resolve("shapes/core"),
                root.resolve("shapes/vocabularies/skos"),
                root.resolve("shapes/integration"),
                root.resolve("shapes/warsampo/local"),
                root.resolve("shapes/warsampo/cross"),
                root.resolve("shapes/warsampo/requirements"))));
        var severity = ResourceFactory.createProperty(Finding.SH + "severity");
        var message = ResourceFactory.createProperty(Finding.SH + "message");
        var source = ResourceFactory.createProperty("http://purl.org/dc/terms/source");
        var layer = ResourceFactory.createProperty(LinterVocabulary.NS + "layer");

        var rules = model.listSubjectsWithProperty(severity).toList();
        var incomplete = rules.stream()
                .filter(rule -> !rule.isURIResource()
                        || !rule.hasProperty(message)
                        || !rule.hasProperty(source)
                        || !rule.hasProperty(layer))
                .map(Object::toString)
                .sorted()
                .toList();

        assertFalse(rules.isEmpty());
        assertTrue(incomplete.isEmpty(), "Rules with incomplete metadata: " + incomplete);
    }

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
    void knownVocabularyTermsMustBeUsedInTheirDeclaredRole() throws Exception {
        ValidationRun run = validate("fixtures/negative/standard-term-role-invalid.ttl", false, null, null);

        assertEquals(2, run.findings().size());
        assertTrue(run.findings().stream().allMatch(finding -> finding.rule().contains("KnownVocabularyTermShape")));
    }

    @Test
    void declaredStandardTermsAreValidated() throws Exception {
        ValidationRun run = validate("fixtures/negative/declared-standard-term-invalid.ttl", false, null, null);

        assertEquals(1, run.findings().size());
        assertTrue(run.findings().getFirst().rule().contains("KnownVocabularyTermShape"));
    }

    @Test
    void multipleObjectsUsingTheSameUnknownPredicateRetainMultiplicity() throws Exception {
        ValidationRun run =
                validate("fixtures/negative/standard-term-multiple-values-invalid.ttl", false, null, null);

        assertEquals(2, run.findings().size());
        assertEquals(2, run.findings().stream().map(Finding::signature).distinct().count());
        assertTrue(run.findings().stream().allMatch(finding -> finding.rule().contains("KnownVocabularyTermShape")));
    }

    @Test
    void namedGraphSyntaxIsRejectedExplicitly() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validate("fixtures/negative/named-graph.trig", false, null, null));

        assertTrue(error.getMessage().contains("named-graph semantics"));
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
        ValidationRun firstRun = validate("fixtures/negative/core-invalid.ttl", false, null, null);
        ValidationRun secondRun = validate("fixtures/negative/core-invalid.ttl", false, null, null);
        Path first = temporaryDirectory.resolve("first.ttl");
        Path second = temporaryDirectory.resolve("second.ttl");

        ShaclReportWriter.write(first, firstRun.findings());
        ShaclReportWriter.write(second, secondRun.findings());

        assertEquals(Files.readString(first), Files.readString(second));
        var report = new RdfLoader().load(first);
        assertFalse(report.isEmpty());
        var component = ResourceFactory.createProperty(Finding.SH + "sourceConstraintComponent");
        assertTrue(report
                .listResourcesWithProperty(org.apache.jena.vocabulary.RDF.type, Finding.VALIDATION_RESULT)
                .toList()
                .stream()
                .allMatch(result -> result.hasProperty(component)));
    }

    @Test
    void structurallyIdenticalBlankNodeFindingsRetainMultiplicity(@TempDir Path temporaryDirectory) throws Exception {
        ValidationRun run = validate("fixtures/negative/blank-node-duplicates.ttl", false, null, null);
        Path reportPath = temporaryDirectory.resolve("blank-nodes.ttl");

        ShaclReportWriter.write(reportPath, run.findings());
        var report = new RdfLoader().load(reportPath);

        assertEquals(2, run.findings().size());
        assertEquals(2, run.findings().stream().map(Finding::signature).distinct().count());
        assertEquals(
                2,
                report.listResourcesWithProperty(org.apache.jena.vocabulary.RDF.type, Finding.VALIDATION_RESULT)
                        .toList()
                        .size());
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

    @Test
    void crossModuleModeValidatesSuccessfullyParsedModules(@TempDir Path temporaryDirectory) throws Exception {
        ValidationOptions options = new ValidationOptions(
                root,
                List.of(
                        root.resolve("fixtures/negative/core-invalid.ttl"),
                        root.resolve("fixtures/negative/ill-typed.ttl")),
                Profile.CORE,
                true,
                null,
                null,
                null,
                null,
                temporaryDirectory.resolve("partial-tdb"));

        ValidationRun run = new ValidationService(options).run();

        assertEquals(1, run.modules());
        assertEquals(1, run.parseFailures().size());
        assertEquals(1, run.findings().size());
        assertTrue(run.findings().stream().noneMatch(finding -> finding.sourceModule().equals("@union")));
    }

    @Test
    void incompleteRunDoesNotWriteReportOrBaseline(@TempDir Path temporaryDirectory) throws Exception {
        Path report = temporaryDirectory.resolve("report.ttl");
        Path baseline = temporaryDirectory.resolve("baseline.tsv");
        ValidationOptions options = new ValidationOptions(
                root,
                List.of(root.resolve("fixtures/negative/ill-typed.ttl")),
                Profile.CORE,
                false,
                report,
                null,
                null,
                baseline,
                null);

        ValidationRun run = new ValidationService(options).run();
        run.writeOutputs(options, System.out);

        assertFalse(Files.exists(report));
        assertFalse(Files.exists(baseline));
    }

    @Test
    void warningBaselineCannotSuppressPromotedViolation(@TempDir Path temporaryDirectory) throws Exception {
        Path baseline = temporaryDirectory.resolve("baseline.tsv");
        Files.writeString(
                baseline,
                "# warsampo-linter baseline v1\nwarning-signature\t<" + Finding.SH + "Warning>\tignored\n");

        assertTrue(Baseline.read(baseline).isEmpty());

        Finding promoted = new Finding(
                "warning-signature",
                "<" + Finding.SH + "Violation>",
                "<https://example.org/Rule>",
                "<" + Finding.SH + "SPARQLConstraintComponent>",
                "<https://example.org/focus>",
                "",
                "",
                "fixture.ttl",
                "promoted");
        ValidationRun run = ValidationRun.of(
                List.of(promoted), List.of(), 1, 0, 1, 0, java.time.Duration.ZERO, Baseline.read(baseline));

        assertTrue(run.hasRegressions());
    }

    @Test
    void malformedBaselineSeverityFailsClosed(@TempDir Path temporaryDirectory) throws Exception {
        Path baseline = temporaryDirectory.resolve("baseline.tsv");
        Files.writeString(baseline, "signature\t<https://example.org/UnknownSeverity>\n");

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> Baseline.read(baseline));

        assertTrue(error.getMessage().contains("Malformed baseline severity"));
    }

    @Test
    void crossModeFindsSkosCycleSplitAcrossFiles(@TempDir Path temporaryDirectory) throws Exception {
        ValidationOptions options = new ValidationOptions(
                root,
                List.of(
                        root.resolve("fixtures/negative/skos-cycle-part-a.ttl"),
                        root.resolve("fixtures/negative/skos-cycle-part-b.ttl")),
                Profile.SKOS,
                true,
                null,
                null,
                null,
                null,
                temporaryDirectory.resolve("skos-cycle-tdb"));

        ValidationRun run = new ValidationService(options).run();

        assertTrue(run.findings().stream().anyMatch(finding ->
                finding.sourceModule().equals("@union") && finding.rule().contains("SkosBroaderCycleShape")));
    }

    @Test
    void validationOutputCannotOverwriteInput() {
        Path data = root.resolve("fixtures/positive/core-valid.ttl");
        ValidationOptions options = new ValidationOptions(
                root,
                List.of(data),
                Profile.CORE,
                false,
                data,
                null,
                null,
                null,
                null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> new ValidationService(options).run());

        assertTrue(error.getMessage().contains("conflicts with selected RDF input"));
    }

    @Test
    void integrationProfileFindsDuplicateIdentifiers(@TempDir Path temporaryDirectory) throws Exception {
        ValidationRun run = validate(
                "fixtures/negative/duplicate-identifier-cross.ttl",
                Profile.CORE,
                true,
                null,
                null,
                temporaryDirectory.resolve("duplicate-tdb"));

        assertEquals(1, run.findings().size());
        assertTrue(run.findings().getFirst().rule().contains("DuplicateIdentifierShape"));
        assertTrue(run.findings().getFirst().isWarning());
    }

    @Test
    void skosProfileAcceptsValidFixtureAndFindsIntegrityViolations() throws Exception {
        ValidationRun valid = validate("fixtures/positive/skos-valid.ttl", Profile.SKOS, false, null, null, null);
        ValidationRun invalid = validate("fixtures/negative/skos-invalid.ttl", Profile.SKOS, false, null, null, null);

        assertTrue(valid.findings().isEmpty());
        assertTrue(invalid.findings().stream().anyMatch(finding -> finding.rule().contains("SkosPreferredLabelLanguageShape")));
        assertTrue(invalid.findings().stream().anyMatch(finding -> finding.rule().contains("SkosLabelDisjointnessShape")));
        assertTrue(invalid.findings().stream().anyMatch(finding -> finding.rule().contains("SkosBroaderSelfShape")));
        assertTrue(invalid.findings().stream().anyMatch(finding -> finding.rule().contains("SkosBroaderCycleShape")));
        assertTrue(invalid.findings().stream().anyMatch(finding -> finding.rule().contains("SkosRelatedSelfShape")));
        assertTrue(invalid.findings().stream().anyMatch(finding -> finding.rule().contains("SkosBroaderRelatedDisjointShape")));
        assertTrue(invalid.findings().stream()
                .filter(finding -> finding.rule().contains("SkosBroaderSelfShape")
                        || finding.rule().contains("SkosRelatedSelfShape"))
                .allMatch(Finding::isWarning));
    }

    @Test
    void warsampoLocalProfileEnforcesEventRoles() throws Exception {
        ValidationRun valid = validate(
                "fixtures/positive/warsampo-local-valid.ttl", Profile.WARSAMPO, false, null, null, null);
        ValidationRun invalid = validate(
                "fixtures/negative/warsampo-local-invalid.ttl", Profile.WARSAMPO, false, null, null, null);

        assertTrue(valid.findings().isEmpty());
        assertTrue(invalid.findings().stream().anyMatch(finding -> finding.rule().contains("BirthPersonPropertyShape")));
        assertTrue(invalid.findings().stream().anyMatch(finding -> finding.rule().contains("DeathPersonPropertyShape")));
        assertTrue(invalid.findings().stream().anyMatch(finding -> finding.rule().contains("UnitJoiningNodeShape")));
        assertTrue(invalid.findings().stream().anyMatch(finding -> finding.rule().contains("MedalAwardingMedalPropertyShape")));
        assertTrue(invalid.findings().stream().anyMatch(finding -> finding.rule().contains("MedalAwardingRecipientPropertyShape")));
    }

    @Test
    void warsampoCrossProfileFindsChronologyAndReferenceErrors(@TempDir Path temporaryDirectory) throws Exception {
        ValidationRun valid = validate(
                "fixtures/positive/warsampo-cross-valid.ttl",
                Profile.WARSAMPO,
                true,
                null,
                null,
                temporaryDirectory.resolve("valid-tdb"));
        ValidationRun invalid = validate(
                "fixtures/negative/warsampo-cross-invalid.ttl",
                Profile.WARSAMPO,
                true,
                null,
                null,
                temporaryDirectory.resolve("invalid-tdb"));

        assertTrue(valid.findings().isEmpty());
        assertTrue(invalid.findings().stream().anyMatch(finding -> finding.rule().contains("BirthDeathChronologyShape")));
        assertTrue(invalid.findings().stream().anyMatch(finding -> finding.rule().contains("InternalReferenceShape")));
        assertTrue(invalid.findings().stream().anyMatch(finding -> finding.rule().contains("EventParticipantTypeShape")));
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
        return validate(data, Profile.CORE, crossModule, baseline, writeBaseline, tdb);
    }

    private ValidationRun validate(
            String data,
            Profile profile,
            boolean crossModule,
            Path baseline,
            Path writeBaseline,
            Path tdb)
            throws Exception {
        ValidationOptions options = new ValidationOptions(
                root,
                List.of(root.resolve(data)),
                profile,
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
