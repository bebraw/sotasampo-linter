package fi.ldf.warsampo.linter;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

final class LinterVocabulary {
    static final String NS = "https://w3id.org/warsampo-linter#";

    static final Resource AUDIT_TARGET = ResourceFactory.createResource(NS + "auditTarget");
    static final Resource AUDIT_TARGET_CLASS = ResourceFactory.createResource(NS + "AuditTarget");
    static final Resource REPAIR = ResourceFactory.createResource(NS + "Repair");
    static final Resource AUTOMATIC = ResourceFactory.createResource(NS + "Automatic");
    static final Property SOURCE_MODULE = ResourceFactory.createProperty(NS + "sourceModule");
    static final Property INTERNAL_NAMESPACE = ResourceFactory.createProperty(NS + "internalNamespace");
    static final Property SAFETY = ResourceFactory.createProperty(NS + "safety");
    static final Property REPAIRS_RULE = ResourceFactory.createProperty(NS + "repairsRule");
    static final Property BAD_IRI = ResourceFactory.createProperty(NS + "badIri");
    static final Property REPLACEMENT_IRI = ResourceFactory.createProperty(NS + "replacementIri");
    static final Property VALIDATION_PROFILE = ResourceFactory.createProperty(NS + "validationProfile");
    static final Property REPAIR_ID = ResourceFactory.createProperty(NS + "repairId");

    private LinterVocabulary() {}
}
