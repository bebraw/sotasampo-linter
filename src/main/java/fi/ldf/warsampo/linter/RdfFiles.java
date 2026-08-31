package fi.ldf.warsampo.linter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.jena.riot.RDFLanguages;

final class RdfFiles {
    private RdfFiles() {}

    static List<Path> discover(List<Path> inputs) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path input : inputs) {
            if (!Files.exists(input)) {
                throw new IllegalArgumentException("RDF input does not exist: " + input);
            }
            if (Files.isRegularFile(input)) {
                requireRdf(input);
                files.add(input.toAbsolutePath().normalize());
                continue;
            }
            try (var stream = Files.walk(input)) {
                stream.filter(Files::isRegularFile)
                        .filter(RdfFiles::isRdf)
                        .map(path -> path.toAbsolutePath().normalize())
                        .forEach(files::add);
            }
        }
        return files.stream().distinct().sorted(Comparator.comparing(Path::toString)).toList();
    }

    static List<Path> discoverRequiredDirectories(List<Path> directories) throws IOException {
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) {
                throw new IllegalArgumentException("Required RDF directory does not exist: " + directory);
            }
        }
        List<Path> files = discover(directories);
        if (files.isEmpty()) {
            throw new IllegalArgumentException("No RDF files found under: " + directories);
        }
        return files;
    }

    static boolean isRdf(Path path) {
        return RDFLanguages.filenameToLang(path.getFileName().toString()) != null;
    }

    private static void requireRdf(Path path) {
        if (!isRdf(path)) {
            throw new IllegalArgumentException("Unsupported RDF filename: " + path);
        }
    }
}
