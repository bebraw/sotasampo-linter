package fi.ldf.warsampo.linter;

import java.nio.file.Path;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandlerFactory;

final class RdfLoader {
    Model load(Path path) {
        Model model = ModelFactory.createDefaultModel();
        parseInto(model, path);
        return model;
    }

    Model loadAll(List<Path> paths) {
        Model model = ModelFactory.createDefaultModel();
        paths.forEach(path -> parseInto(model, path));
        return model;
    }

    void parseInto(Model model, Path path) {
        Lang language = RDFLanguages.filenameToLang(path.getFileName().toString());
        if (language == null) {
            throw new IllegalArgumentException("Cannot determine RDF language for " + path);
        }
        RDFParser.create()
                .source(path.toString())
                .lang(language)
                .base(path.toUri().toString())
                .errorHandler(ErrorHandlerFactory.errorHandlerStrict)
                .parse(model);
    }
}
