#!/usr/bin/env sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
jar_path="$project_root/target/warsampo-linter.jar"

if [ ! -f "$jar_path" ]; then
  "$project_root/scripts/mvn.sh" package
fi

docker run --rm \
  --volume "$project_root:/workspace" \
  --workdir /workspace \
  eclipse-temurin:21-jre \
  java -jar /workspace/target/warsampo-linter.jar "$@"
