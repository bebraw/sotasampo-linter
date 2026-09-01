# Invalid event-date correction proposal

This proposal resolves the two invalid `xsd:date` values discovered by strict parsing of `warsa-event-data/times.ttl`.

## Parissavaara: `1939-12-35`

Repoint `warsa:events/event_1481` from `time_1939-12-22-1939-12-35` to the existing canonical resource `time_1939-12-22-1939-12-23`, then remove the orphan invalid definition.

Evidence:

- The companion Parissavaara event for II/JR 41, `event_1478`, already uses December 22–23.
- `event_1481` represents III/JR 41 at the same place and start date.
- [Ilomantsin Sotatie](https://sotatie.fi/sotatie/kohdekuvaukset/parissavaara) describes the two-battalion attack ordered for the evening of December 22.
- [Liberation Route Europe](https://www.liberationroute.com/en/pois/3180) independently describes two Finnish battalions attacking Parissavaara on December 22, 1939.

The repository-local companion event is the strongest evidence for an intended end date of December 23. The external sources corroborate that the battalions participated in the same action.

## Pehmo–Siankärsä–Mannikkala: `1940-02-30`

No defensible historical day could be established from the exported RDF or accessible sources. The proposal therefore does not silently replace the source value with February 29 or March 1. It:

- renames the time span to `time_unknown-1940-03-03`;
- removes the invalid lower bound;
- retains the known March 3 upper bound; and
- records the invalid source value and unresolved status in `crm:P3_has_note`.

This follows the linter ADR's rule that cleanup must not infer historical truth merely to make a graph conform.

## Input and corrected checksums

The inputs are the untouched files from the [WarSampo Knowledge Graph 2.0.0 archive](https://doi.org/10.5281/zenodo.3431122). The corrected hashes are the byte-for-byte results of applying `correction.patch` to that archive.

| Module | Before SHA-256 | Corrected SHA-256 |
| --- | --- | --- |
| `warsa-event-data/output.ttl` | `fe1556e9b57033a620fbcdd09acd75058f33b9c0f6046dbc61200501f1fb9588` | `c6db7d7f62314a2535f06290fe534cf0fde75b28e7fbe457fe129c67b227b178` |
| `warsa-event-data/times.ttl` | `0ef4aa1a0d03431eda218d54a06f9e433a3db2389ee32ae61426a0fddbe4147e` | `a363937fe807ef83e97ce8f1cd84015dd350cc7eead168171f28abff10bb396e` |

The patch was verified in the forward direction against a fresh extraction of the untouched Zenodo archive. The corrected local time module strictly parses and validates under the Core profile: 139,300 triples, zero findings, and zero parse failures. A complete module-local run then parsed all 61 modules and 13,873,089 triples with no parse failures; it produced the same 123 violations and one warning as the reviewed 60-module baseline. The corpus is ignored and has no owning Git checkout in this workspace; `correction.patch` is the portable artifact to submit to the source repository or generation pipeline.
