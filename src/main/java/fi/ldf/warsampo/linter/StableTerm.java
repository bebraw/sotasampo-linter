package fi.ldf.warsampo.linter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.apache.jena.graph.Node;
import org.apache.jena.riot.out.NodeFmtLib;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;

final class StableTerm {
    private StableTerm() {}

    static String format(RDFNode node, Model context) {
        if (node == null) {
            return "";
        }
        return format(node.asNode(), context, new HashSet<>(), 0);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String format(Node node, Model context, Set<Node> seen, int depth) {
        if (!node.isBlank()) {
            return NodeFmtLib.strNT(node);
        }
        if (depth >= 16 || !seen.add(node)) {
            return "_:cycle";
        }

        List<String> neighborhood = new ArrayList<>();
        context.getGraph().find(node, Node.ANY, Node.ANY).forEachRemaining(triple -> neighborhood.add(
                "S " + format(triple.getPredicate(), context, new HashSet<>(seen), depth + 1)
                        + " " + format(triple.getObject(), context, new HashSet<>(seen), depth + 1)));
        context.getGraph().find(Node.ANY, Node.ANY, node).forEachRemaining(triple -> neighborhood.add(
                "O " + format(triple.getSubject(), context, new HashSet<>(seen), depth + 1)
                        + " " + format(triple.getPredicate(), context, new HashSet<>(seen), depth + 1)));
        neighborhood.sort(Comparator.naturalOrder());
        return "_:sha256-" + sha256(String.join("\n", neighborhood));
    }
}
