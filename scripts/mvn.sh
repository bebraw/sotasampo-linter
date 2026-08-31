#!/usr/bin/env sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

docker run --rm \
  --volume "$project_root:/workspace" \
  --volume warsampo-linter-maven-cache:/root/.m2 \
  --workdir /workspace \
  maven:3.9.11-eclipse-temurin-21 \
  mvn -B -ntp "$@"
