#!/usr/bin/env sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

docker run --rm \
  --env PIP_ROOT_USER_ACTION=ignore \
  --volume "$project_root:/workspace" \
  --volume warsampo-linter-pip-cache:/root/.cache/pip \
  --workdir /workspace \
  python:3.13-slim \
  sh -c 'pip install --quiet --requirement requirements-dev.txt && python scripts/generate_vocabulary_manifest.py "$@"' \
  -- "$@"
