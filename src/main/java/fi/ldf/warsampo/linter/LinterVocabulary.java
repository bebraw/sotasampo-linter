package fi.ldf.warsampo.linter;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

final class LinterVocabulary {
    static final String NS = "https://w3id.org/warsampo-linter#";

    static final Resource AUDIT_TARGET = ResourceFactory.createResource(NS + "auditTarget");
    static final Resource AUDIT_TARGET_CLASS = ResourceFactory.createResource(NS + "AuditTarget");
    static final Property SOURCE_MODULE = ResourceFactory.createProperty(NS + "sourceModule");
    static final Property INTERNAL_NAMESPACE = ResourceFactory.createProperty(NS + "internalNamespace");

    private LinterVocabulary() {}
}
