package fi.ldf.warsampo.linter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

enum Profile {
    CORE,
    SKOS,
    WARSAMPO;

    static Profile parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown profile `" + value + "`; expected core, skos, or warsampo.");
        }
    }

    List<Path> localShapeDirectories(Path root) {
        List<Path> paths = new ArrayList<>();
        paths.add(root.resolve("shapes/core"));
        if (this == SKOS || this == WARSAMPO) {
            paths.add(root.resolve("shapes/vocabularies/skos"));
        }
        if (this == WARSAMPO) {
            paths.add(root.resolve("shapes/warsampo/local"));
        }
        return List.copyOf(paths);
    }

    List<Path> crossShapeDirectories(Path root) {
        List<Path> paths = new ArrayList<>();
        paths.add(root.resolve("shapes/integration"));
        if (this == WARSAMPO) {
            paths.add(root.resolve("shapes/warsampo/cross"));
        }
        return List.copyOf(paths);
    }

    List<Path> shapeDirectories(Path root, boolean crossModule) {
        List<Path> paths = new ArrayList<>(localShapeDirectories(root));
        if (crossModule) {
            paths.addAll(crossShapeDirectories(root));
        }
        return List.copyOf(paths);
    }
}
