# Attach the WarSampo dataset

The project uses the [WarSampo Knowledge Graph 2.0.0 archive](https://doi.org/10.5281/zenodo.3431122) as its reproducible study snapshot. The [WarSampo Linked Data Finland page](https://www.ldf.fi/dataset/warsa) describes the published dataset and services, but the pinned Zenodo archive preserves the 61 source-module files expected by this linter.

## Snapshot identity

| Property | Value |
| --- | --- |
| Version | 2.0.0 |
| Published | 2019-10-17 |
| Archive | `https://zenodo.org/record/3431122/files/warsampo.zip` |
| DOI | `10.5281/zenodo.3431122` |
| Archive size | 48,755,820 bytes |
| Archive MD5 | `5874cab39e126dddab527c7e29bd8c17` |
| Turtle modules | 61 |
| Extracted size | 711,351,233 bytes (about 679 MiB) |

Allow at least 1 GiB of free disk space for the download, extraction, and validation outputs. The resulting `warsampo/` directory is ignored by Git and must not be committed.

## Recommended setup

From the repository root, run:

```sh
./scripts/fetch-warsampo.sh
```

The script requires `curl`, `unzip`, `patch`, and an MD5 implementation (`md5sum`, macOS `md5`, or `openssl`). It:

1. downloads the pinned archive to a temporary directory;
2. verifies the published MD5 checksum;
3. verifies that the archive contains 61 Turtle modules;
4. extracts the modules into `warsampo/`; and
5. applies the reviewed [invalid-date correction](../../proposals/source-corrections/2026-09-01-invalid-dates/README.md).

It refuses to overwrite an existing `warsampo/` directory. Move or remove that directory yourself if you intentionally want a fresh copy.

The correction is necessary because the untouched archive contains the invalid `xsd:date` values `1939-12-35` and `1940-02-30`. Without the correction, strict parsing exits with status `2` and intentionally suppresses report and baseline publication.

## Manual setup

The equivalent manual process is:

```sh
curl --fail --location \
  https://zenodo.org/record/3431122/files/warsampo.zip \
  --output /tmp/warsampo.zip

# Linux: md5sum /tmp/warsampo.zip
# macOS: md5 -q /tmp/warsampo.zip
# Expected: 5874cab39e126dddab527c7e29bd8c17

mkdir warsampo
unzip -q /tmp/warsampo.zip -d warsampo
patch --batch --forward -p1 -d warsampo \
  < proposals/source-corrections/2026-09-01-invalid-dates/correction.patch
```

Do not proceed if the archive checksum differs from the pinned value.

## Run the reviewed module-local validation

Docker is the only additional runtime prerequisite. From the repository root:

```sh
./scripts/linter.sh validate \
  --data warsampo \
  --profile warsampo \
  --summary reports/warsampo-local-summary.txt \
  --baseline baselines/warsampo-local.tsv
```

For the corrected snapshot, the reviewed result is 61 parsed modules, 13,873,089 triples, 123 violations, one warning, no new violations, and exit status `0`. The accepted findings and environment are documented in the [module-local baseline record](../baselines/2026-09-01-warsampo.md).

## Optional cross-module audit

The integration audit builds a disk-backed union graph and exercises rules whose evidence can be split across source modules:

```sh
./scripts/linter.sh validate \
  --data warsampo \
  --profile warsampo \
  --cross-module \
  --summary reports/warsampo-union-summary.txt \
  --baseline baselines/warsampo-local.tsv
```

This is intentionally not the normal quick check. The reviewed run took about 6 minutes 30 seconds and returned exit status `1` because it exposed 1,643 union-level violations not accepted by the module-local baseline. See the [cross-module audit record](../baselines/2026-09-01-warsampo-union.md) before interpreting those results.

## Troubleshooting

- `warsampo/ already exists`: the fetcher will not overwrite local data. Move or remove the directory intentionally, then rerun it.
- `archive checksum mismatch`: discard the download and check the Zenodo record or network path; do not run against unverified bytes.
- `unexpected Turtle module count`: the archive does not match the pinned 2.0.0 layout.
- Docker cannot see the data: keep the directory at the repository-root path `warsampo/`; the wrapper mounts the repository as `/workspace` in the container.
