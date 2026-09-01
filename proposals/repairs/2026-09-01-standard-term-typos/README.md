# Standard-term typo repair proposal: 2026-09-01

This proposal was generated from three real WarSampo modules by the guarded repair command. It plans 15 exact IRI substitutions and does not modify the source checkout.

## Contents

- `repair.ru`: the five applicable, idempotent SPARQL Update blocks.
- `provenance.ttl`: 15 `dash:GraphUpdate` records containing the source module, repair and rule identifiers, and exact deleted/added statements.

## Input snapshot

| Source module | SHA-256 |
| --- | --- |
| `warsampo/ammo-data/ammo_schema.ttl` | `062544f9c93f93d1ee44c1e792603f85d42afea943cf59072083606cd0ac1434` |
| `warsampo/ammo-data/sources.ttl` | `e1835c2b9ef15316d77fa3caee71696d911d33a201c9c4319737426cb812602b` |
| `warsampo/warsa-actor-data/medals/medal_types.ttl` | `3df0c8c118c253fd803f9f3c5926241a67d19e0416bde29ab5c6cc0d57be2bf8` |

The proposal was generated at `2026-09-01T06:37:21.769097680Z`. Regenerate it if any input checksum differs.

## Verification

The proposal was applied to new copies under the ignored `reports/` directory. The repair engine reported exactly 15 applied triple changes. The three repaired copies were then validated with the cumulative SKOS profile:

```text
Validated 3 module(s), 578 triples
Rules: 8
Findings: 0 violation(s), 0 warning(s), 0 info
New violations: 0
```

The original source checksums were unchanged after verification. Apply mode also generated mandatory patch and provenance copies inside the staged output directory.

## Review and application

Review `repair.ru` and `provenance.ttl` together. Application to maintained source repositories should happen through their normal review workflow; this repository deliberately does not overwrite the ignored WarSampo checkout.
