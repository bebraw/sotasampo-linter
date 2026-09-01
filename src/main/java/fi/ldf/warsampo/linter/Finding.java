package fi.ldf.warsampo.linter;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

record Finding(
        String signature,
        String severity,
        String rule,
        String constraintComponent,
        String focus,
        String path,
        String value,
        String sourceModule,
        String message) {

    static final String SH = "http://www.w3.org/ns/shacl#";
    static final Resource VALIDATION_RESULT = ResourceFactory.createResource(SH + "ValidationResult");
    static final Property SOURCE_SHAPE = ResourceFactory.createProperty(SH + "sourceShape");
    static final Property SOURCE_CONSTRAINT_COMPONENT = ResourceFactory.createProperty(SH + "sourceConstraintComponent");
    static final Property RESULT_SEVERITY = ResourceFactory.createProperty(SH + "resultSeverity");
    static final Property FOCUS_NODE = ResourceFactory.createProperty(SH + "focusNode");
    static final Property RESULT_PATH = ResourceFactory.createProperty(SH + "resultPath");
    static final Property VALUE = ResourceFactory.createProperty(SH + "value");
    static final Property RESULT_MESSAGE = ResourceFactory.createProperty(SH + "resultMessage");

    static final Comparator<Finding> ORDER = Comparator.comparing(Finding::signature)
            .thenComparing(Finding::sourceModule)
            .thenComparing(Finding::message);

    static Finding from(Resource result, Model reportModel, Model dataModel, String module) {
        String severity = term(result, RESULT_SEVERITY, reportModel);
        String rule = term(result, SOURCE_SHAPE, reportModel);
        String constraintComponent = term(result, SOURCE_CONSTRAINT_COMPONENT, reportModel);
        String focus = term(result, FOCUS_NODE, dataModel);
        String path = term(result, RESULT_PATH, reportModel);
        String value = term(result, VALUE, dataModel);
        String message = lexical(result, RESULT_MESSAGE);
        String material = String.join("\u001f", rule, constraintComponent, focus, path, value, module);
        return new Finding(
                StableTerm.sha256(material),
                severity,
                rule,
                constraintComponent,
                focus,
                path,
                value,
                module,
                message);
    }

    static List<Finding> normalizeOccurrences(List<Finding> findings) {
        Map<String, Integer> occurrences = new HashMap<>();
        return findings.stream()
                .sorted(Comparator.comparing(Finding::signature).thenComparing(Finding::semanticKey))
                .map(finding -> {
                    int occurrence = occurrences.merge(finding.signature(), 1, Integer::sum);
                    return finding.withSignature(StableTerm.sha256(finding.signature() + "\u001f" + occurrence));
                })
                .sorted(ORDER)
                .toList();
    }

    String semanticKey() {
        return String.join("\u001f", severity, rule, constraintComponent, focus, path, value, message);
    }

    private Finding withSignature(String replacement) {
        return new Finding(
                replacement,
                severity,
                rule,
                constraintComponent,
                focus,
                path,
                value,
                sourceModule,
                message);
    }

    boolean isViolation() {
        return severity.equals("<" + SH + "Violation>");
    }

    boolean isWarning() {
        return severity.equals("<" + SH + "Warning>");
    }

    private static String term(Resource resource, Property property, Model model) {
        RDFNode node = resource.getPropertyResourceValue(property);
        if (node == null && resource.getProperty(property) != null) {
            node = resource.getProperty(property).getObject();
        }
        return StableTerm.format(node, model);
    }

    private static String lexical(Resource resource, Property property) {
        var statement = resource.getProperty(property);
        if (statement == null) {
            return "";
        }
        RDFNode object = statement.getObject();
        return object.isLiteral() ? object.asLiteral().getLexicalForm() : object.toString();
    }
}
