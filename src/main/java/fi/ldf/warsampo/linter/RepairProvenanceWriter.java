package fi.ldf.warsampo.linter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;

final class RepairProvenanceWriter {
    private static final String DASH = "http://datashapes.org/dash#";
    private static final String PROV = "http://www.w3.org/ns/prov#";
    private static final Resource REPAIR_RUN = ResourceFactory.createResource(LinterVocabulary.NS + "RepairRun");
    private static final Resource GRAPH_UPDATE = ResourceFactory.createResource(DASH + "GraphUpdate");
    private static final Property CHANGE = ResourceFactory.createProperty(LinterVocabulary.NS + "change");
    private static final Property APPLIED = ResourceFactory.createProperty(LinterVocabulary.NS + "applied");
    private static final Property ADDED_TRIPLE = ResourceFactory.createProperty(DASH + "addedTriple");
    private static final Property DELETED_TRIPLE = ResourceFactory.createProperty(DASH + "deletedTriple");
    private static final Property GENERATED_AT_TIME = ResourceFactory.createProperty(PROV + "generatedAtTime");

    private RepairProvenanceWriter() {}

    static Model create(List<TripleChange> changes, boolean applied, Instant timestamp) {
        Model model = ModelFactory.createDefaultModel();
        String material = timestamp + "\u001f" + changes.stream().map(TripleChange::sortKey).reduce("", (a, b) -> a + "\n" + b);
        Resource run = model.createResource("urn:warsampo-linter:repair-run:" + StableTerm.sha256(material));
        run.addProperty(RDF.type, REPAIR_RUN)
                .addLiteral(APPLIED, applied)
                .addLiteral(
                        GENERATED_AT_TIME,
                        model.createTypedLiteral(timestamp.toString(), XSD.dateTime.getURI()));

        for (TripleChange change : changes) {
            String changeHash = StableTerm.sha256(change.sortKey());
            Resource graphUpdate = model.createResource("urn:warsampo-linter:graph-update:" + changeHash);
            Resource deleted = statement(model, change.before(), changeHash + ":deleted");
            Resource added = statement(model, change.after(), changeHash + ":added");
            graphUpdate.addProperty(RDF.type, GRAPH_UPDATE)
                    .addProperty(LinterVocabulary.REPAIR_ID, model.createResource(change.repair().id()))
                    .addProperty(LinterVocabulary.REPAIRS_RULE, model.createResource(change.repair().ruleId()))
                    .addLiteral(LinterVocabulary.SOURCE_MODULE, change.sourceModule())
                    .addProperty(DELETED_TRIPLE, deleted)
                    .addProperty(ADDED_TRIPLE, added);
            run.addProperty(CHANGE, graphUpdate);
        }
        return model;
    }

    static void write(Path path, Model model) throws IOException {
        Baseline.ensureParent(path);
        Files.writeString(path, serialize(model), StandardCharsets.UTF_8);
    }

    static String serialize(Model model) {
        TreeSet<String> statements = new TreeSet<>();
        model.getGraph().find().forEachRemaining(triple -> statements.add(TripleChange.format(triple)));
        List<String> lines = new ArrayList<>();
        lines.add("# Deterministic N-Triples subset of Turtle");
        lines.addAll(statements);
        return String.join("\n", lines) + "\n";
    }

    private static Resource statement(Model model, Triple triple, String suffix) {
        Resource statement = model.createResource("urn:warsampo-linter:statement:" + suffix);
        statement.addProperty(RDF.type, RDF.Statement)
                .addProperty(RDF.subject, model.asRDFNode(triple.getSubject()))
                .addProperty(RDF.predicate, model.asRDFNode(triple.getPredicate()))
                .addProperty(RDF.object, model.asRDFNode(triple.getObject()));
        return statement;
    }
}
