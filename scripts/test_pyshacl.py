from pathlib import Path

from pyshacl import validate
from rdflib import Graph, Literal, Namespace, RDF


ROOT = Path(__file__).resolve().parent.parent
LINTER = Namespace("https://w3id.org/warsampo-linter#")
SH = Namespace("http://www.w3.org/ns/shacl#")


def load_graph(paths: list[Path]) -> Graph:
    graph = Graph()
    for path in paths:
        graph.parse(path, format="turtle")
    return graph


def fixture_result(path: str) -> tuple[bool, Graph, str]:
    data = load_graph(
        [
            ROOT / path,
            ROOT / "vocabularies/known-terms.ttl",
        ]
    )
    data.add((LINTER.auditTarget, RDF.type, LINTER.AuditTarget))
    data.add(
        (
            LINTER.auditTarget,
            LINTER.internalNamespace,
            Literal("http://ldf.fi/warsa/"),
        )
    )
    shapes = load_graph(sorted((ROOT / "shapes/core").glob("*.ttl")))
    conforms, report, report_text = validate(data, shacl_graph=shapes, advanced=False)
    return bool(conforms), report, report_text


def main() -> None:
    positive_conforms, _, positive_report_text = fixture_result(
        "fixtures/positive/core-valid.ttl"
    )
    negative_conforms, negative_report, _ = fixture_result(
        "fixtures/negative/core-invalid.ttl"
    )

    if not positive_conforms:
        raise SystemExit(
            "pySHACL rejected the positive Core fixture:\n" + positive_report_text
        )
    if negative_conforms:
        raise SystemExit("pySHACL accepted the negative Core fixture")

    result_count = len(list(negative_report.subjects(RDF.type, SH.ValidationResult)))
    if result_count != 1:
        raise SystemExit(f"expected one pySHACL result, received {result_count}")

    print("pySHACL compatibility: positive fixture conforms; negative fixture reports one violation")


if __name__ == "__main__":
    main()
