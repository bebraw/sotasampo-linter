package fi.ldf.warsampo.linter;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.apache.jena.rdf.model.Model;

record RepairRun(
        List<TripleChange> changes,
        String patch,
        Model provenance,
        Instant timestamp,
        boolean applied,
        List<Path> writtenFiles) {

    void writeOutputs(RepairOptions options, PrintStream out) throws IOException {
        Path patchPath = options.effectivePatchPath();
        Path provenancePath = options.effectiveProvenancePath();
        if (patchPath != null && patchPath.equals(provenancePath)) {
            throw new IllegalArgumentException("SPARQL Update and provenance paths must differ: " + patchPath);
        }
        requireAbsent(patchPath, "repair output");
        requireAbsent(provenancePath, "repair provenance");

        out.println((applied ? "Applied" : "Planned") + " " + changes.size() + " triple repair(s)");
        out.println("Source files were " + (applied ? "copied to " + options.outputDirectory() : "not modified (dry run)"));
        out.println("Repair timestamp: " + timestamp);

        if (patchPath == null) {
            out.println("\n--- SPARQL Update ---");
            out.print(patch);
        } else {
            writeNewFile(patchPath, patch);
            out.println("SPARQL Update: " + patchPath);
        }

        String provenanceText = RepairProvenanceWriter.serialize(provenance);
        if (provenancePath == null) {
            out.println("\n--- repair provenance (Turtle) ---");
            out.print(provenanceText);
        } else {
            RepairProvenanceWriter.write(provenancePath, provenance);
            out.println("Provenance: " + provenancePath);
        }
    }

    private static void writeNewFile(Path path, String content) throws IOException {
        Baseline.ensureParent(path);
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static void requireAbsent(Path path, String kind) {
        if (path != null && Files.exists(path)) {
            throw new IllegalArgumentException("Refusing to overwrite " + kind + ": " + path);
        }
    }
}
