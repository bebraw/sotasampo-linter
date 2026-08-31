package fi.ldf.warsampo.linter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.vocabulary.RDF;

final class ProfileValidationContext {
    private final Shapes shapes;
    private final Model supportModel;

    private ProfileValidationContext(Shapes shapes, Model supportModel) {
        this.shapes = shapes;
        this.supportModel = supportModel;
    }

    static ProfileValidationContext load(Path root, Profile profile) throws IOException {
        RdfLoader loader = new RdfLoader();
        List<Path> shapeFiles = RdfFiles.discoverRequiredDirectories(profile.shapeDirectories(root, false));
        Shapes shapes = Shapes.parse(loader.loadAll(shapeFiles).getGraph());
        Model support = loader.loadAll(RdfFiles.discoverRequiredDirectories(List.of(root.resolve("vocabularies"))));
        support.add(LinterVocabulary.AUDIT_TARGET, RDF.type, LinterVocabulary.AUDIT_TARGET_CLASS);
        support.add(LinterVocabulary.AUDIT_TARGET, LinterVocabulary.INTERNAL_NAMESPACE, "http://ldf.fi/warsa/");
        return new ProfileValidationContext(shapes, support);
    }

    List<Finding> validate(Model source, String module) {
        Model data = ModelFactory.createUnion(source, supportModel);
        ValidationReport report = ShaclValidator.get().validate(shapes, data.getGraph());
        Model reportModel = ModelFactory.createModelForGraph(report.getGraph());
        List<Resource> resources = reportModel
                .listResourcesWithProperty(RDF.type, Finding.VALIDATION_RESULT)
                .toList();
        return resources.stream()
                .map(resource -> Finding.from(resource, reportModel, module))
                .sorted(Finding.ORDER)
                .toList();
    }
}
