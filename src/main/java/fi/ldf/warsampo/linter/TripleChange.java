package fi.ldf.warsampo.linter;

import java.util.Comparator;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.out.NodeFmtLib;

record TripleChange(
        String sourceModule,
        RepairDefinition repair,
        Triple before,
        Triple after,
        boolean replacementAdded) {
    static final Comparator<TripleChange> ORDER = Comparator.comparing(TripleChange::sortKey);

    String sortKey() {
        return String.join(
                "\u001f", sourceModule, repair.id(), format(before), format(after), Boolean.toString(replacementAdded));
    }

    static String format(Triple triple) {
        return NodeFmtLib.strNT(triple.getSubject())
                + " "
                + NodeFmtLib.strNT(triple.getPredicate())
                + " "
                + NodeFmtLib.strNT(triple.getObject())
                + " .";
    }
}
