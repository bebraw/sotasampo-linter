package fi.ldf.warsampo.linter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.vocabulary.RDF;

final class RepairCatalog {
    private static final String DASH = "http://datashapes.org/dash#";
    private static final String SH = "http://www.w3.org/ns/shacl#";
    private static final Property SUGGESTION_GENERATOR = ResourceFactory.createProperty(DASH + "suggestionGenerator");
    private static final Property UPDATE = ResourceFactory.createProperty(SH + "update");

    private RepairCatalog() {}

    static List<RepairDefinition> load(Path root, Set<String> requestedIds, boolean allAutomatic)
            throws IOException {
        List<Path> files = RdfFiles.discoverRequiredDirectories(List.of(root.resolve("repairs/core")));
        Model model = new RdfLoader().loadAll(files);
        List<RepairDefinition> definitions = model
                .listResourcesWithProperty(RDF.type, LinterVocabulary.REPAIR)
                .toList()
                .stream()
                .map(RepairCatalog::from)
                .sorted(Comparator.comparing(RepairDefinition::id))
                .toList();

        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("The repair catalog contains no repair definitions.");
        }
        Set<String> seenIds = new HashSet<>();
        for (RepairDefinition definition : definitions) {
            if (!seenIds.add(definition.id())) {
                throw new IllegalArgumentException("Duplicate repair ID: " + definition.id());
            }
        }

        if (allAutomatic) {
            return definitions;
        }

        List<RepairDefinition> selected = new ArrayList<>();
        Set<String> unmatched = new HashSet<>(requestedIds);
        for (RepairDefinition definition : definitions) {
            if (requestedIds.contains(definition.id()) || requestedIds.contains(definition.localId())) {
                selected.add(definition);
                unmatched.remove(definition.id());
                unmatched.remove(definition.localId());
            }
        }
        if (!unmatched.isEmpty()) {
            throw new IllegalArgumentException("Unknown repair ID(s): " + String.join(", ", unmatched));
        }
        return List.copyOf(selected);
    }

    private static RepairDefinition from(Resource resource) {
        if (!resource.isURIResource()) {
            throw new IllegalArgumentException("Every repair definition must have a stable IRI.");
        }
        Resource safety = requiredResource(resource, LinterVocabulary.SAFETY);
        if (!LinterVocabulary.AUTOMATIC.equals(safety)) {
            throw new IllegalArgumentException("Unsupported repair safety tier for " + resource.getURI());
        }
        Resource rule = requiredResource(resource, LinterVocabulary.REPAIRS_RULE);
        Resource badIri = requiredResource(resource, LinterVocabulary.BAD_IRI);
        Resource replacementIri = requiredResource(resource, LinterVocabulary.REPLACEMENT_IRI);
        String validationProfile = requiredLiteral(resource, LinterVocabulary.VALIDATION_PROFILE);
        Resource generator = requiredResource(resource, SUGGESTION_GENERATOR);
        String update = requiredLiteral(generator, UPDATE);
        UpdateFactory.create(update);

        if (!rule.isURIResource() || !badIri.isURIResource() || !replacementIri.isURIResource()) {
            throw new IllegalArgumentException("Repair metadata must use IRIs for " + resource.getURI());
        }
        if (badIri.equals(replacementIri)) {
            throw new IllegalArgumentException("Repair replacement must differ from its bad IRI: " + resource.getURI());
        }
        String localId = resource.getLocalName();
        if (localId == null || localId.isBlank()) {
            localId = resource.getURI();
        }
        return new RepairDefinition(
                resource.getURI(),
                localId,
                rule.getURI(),
                badIri.getURI(),
                replacementIri.getURI(),
                Profile.parse(validationProfile),
                update.strip());
    }

    private static Resource requiredResource(Resource subject, Property property) {
        RDFNode value = required(subject, property);
        if (!value.isResource()) {
            throw new IllegalArgumentException(property.getURI() + " must identify a resource on " + subject);
        }
        return value.asResource();
    }

    private static String requiredLiteral(Resource subject, Property property) {
        RDFNode value = required(subject, property);
        if (!value.isLiteral()) {
            throw new IllegalArgumentException(property.getURI() + " must be a literal on " + subject);
        }
        return value.asLiteral().getString();
    }

    private static RDFNode required(Resource subject, Property property) {
        List<RDFNode> values = subject.listProperties(property).mapWith(statement -> statement.getObject()).toList();
        if (values.size() != 1) {
            throw new IllegalArgumentException(
                    "Expected exactly one " + property.getURI() + " value on " + subject + ", found " + values.size());
        }
        return values.getFirst();
    }
}
