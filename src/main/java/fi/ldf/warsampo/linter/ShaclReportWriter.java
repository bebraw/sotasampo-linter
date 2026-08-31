package fi.ldf.warsampo.linter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.riot.out.NodeFmtLib;

final class ShaclReportWriter {
    private static final String REPORT = "<urn:warsampo-linter:validation-report>";
    private static final String RDF_TYPE = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>";
    private static final String SH = "http://www.w3.org/ns/shacl#";
    private static final String LINTER = LinterVocabulary.NS;

    private ShaclReportWriter() {}

    static void write(Path path, List<Finding> findings) throws IOException {
        Baseline.ensureParent(path);
        List<String> triples = new ArrayList<>();
        triples.add(triple(REPORT, RDF_TYPE, iri(SH + "ValidationReport")));
        triples.add(triple(REPORT, iri(SH + "conforms"), literal(Boolean.toString(findings.isEmpty()),
                "http://www.w3.org/2001/XMLSchema#boolean")));

        for (Finding finding : findings) {
            String result = iri("urn:warsampo-linter:result:" + finding.signature());
            triples.add(triple(REPORT, iri(SH + "result"), result));
            triples.add(triple(result, RDF_TYPE, iri(SH + "ValidationResult")));
            addTerm(triples, result, SH + "resultSeverity", finding.severity());
            addTerm(triples, result, SH + "sourceShape", finding.rule());
            addTerm(triples, result, SH + "focusNode", finding.focus());
            addTerm(triples, result, SH + "resultPath", finding.path());
            addTerm(triples, result, SH + "value", finding.value());
            if (!finding.message().isEmpty()) {
                triples.add(triple(result, iri(SH + "resultMessage"), literal(finding.message())));
            }
            triples.add(triple(result, iri(LINTER + "sourceModule"), literal(finding.sourceModule())));
            triples.add(triple(result, iri(LINTER + "signature"), literal(finding.signature())));
        }

        List<String> lines = new ArrayList<>();
        lines.add("# Deterministic N-Triples subset of Turtle");
        lines.addAll(new TreeSet<>(triples));
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private static void addTerm(List<String> triples, String subject, String predicate, String term) {
        if (!term.isEmpty()) {
            triples.add(triple(subject, iri(predicate), term));
        }
    }

    private static String triple(String subject, String predicate, String object) {
        return subject + " " + predicate + " " + object + " .";
    }

    private static String iri(String value) {
        return "<" + value + ">";
    }

    private static String literal(String value) {
        return NodeFmtLib.strNT(NodeFactory.createLiteralString(value));
    }

    private static String literal(String value, String datatype) {
        return NodeFmtLib.strNT(NodeFactory.createLiteralDT(value, NodeFactory.getType(datatype)));
    }
}
