#!/usr/bin/env sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
jar_path="$project_root/target/warsampo-linter.jar"

if [ ! -f "$jar_path" ] || find \
  "$project_root/pom.xml" \
  "$project_root/src" \
  "$project_root/shapes" \
  "$project_root/vocabularies" \
  -type f -newer "$jar_path" -print -quit | grep -q .; then
  "$project_root/scripts/mvn.sh" package
fi

docker run --rm \
  --env JAVA_TOOL_OPTIONS=-Xmx4g \
  --volume "$project_root:/workspace" \
  --workdir /workspace \
  eclipse-temurin:21-jre \
  java -jar /workspace/target/warsampo-linter.jar "$@"
