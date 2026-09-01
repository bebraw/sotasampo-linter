# Repairs

Guarded repair definitions and their generated SPARQL Updates live here. Validation never applies these repairs implicitly.

Each automatic repair declares the validation rule it addresses and its minimum `linter:validationProfile`. A run validates with the broader of that profile and the profile requested on the command line, requires a corresponding finding, checks the executed update against the exact expected graph delta, validates postconditions, rejects new violations, and verifies idempotence in tests.

Dry runs emit or atomically write a reviewable SPARQL Update and provenance. Apply runs never rewrite source files: they construct a new destination in a sibling staging directory and publish it only after all RDF copies plus mandatory `.warsampo-linter/repair.ru` and `.warsampo-linter/provenance.ttl` files are complete. Existing destinations or custom metadata paths are rejected during preflight.
