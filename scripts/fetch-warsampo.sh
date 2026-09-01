#!/usr/bin/env sh
set -eu

archive_url="https://zenodo.org/record/3431122/files/warsampo.zip"
archive_md5="5874cab39e126dddab527c7e29bd8c17"
expected_ttl_files=61

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
destination="$project_root/warsampo"
correction_patch="$project_root/proposals/source-corrections/2026-09-01-invalid-dates/correction.patch"

fail() {
  printf '%s\n' "Error: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

calculate_md5() {
  if command -v md5sum >/dev/null 2>&1; then
    md5sum "$1" | awk '{print $1}'
  elif command -v md5 >/dev/null 2>&1; then
    md5 -q "$1"
  elif command -v openssl >/dev/null 2>&1; then
    openssl dgst -md5 "$1" | awk '{print $NF}'
  else
    fail "an MD5 tool is required: install md5sum, md5, or openssl"
  fi
}

[ ! -e "$destination" ] || fail "$destination already exists; move or remove it before fetching a fresh snapshot"
[ -f "$correction_patch" ] || fail "correction patch not found: $correction_patch"

require_command awk
require_command curl
require_command find
require_command mktemp
require_command patch
require_command tr
require_command unzip
require_command wc

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/warsampo-zenodo.XXXXXX")
trap 'rm -rf "$temporary_directory"' EXIT HUP INT TERM

archive="$temporary_directory/warsampo.zip"
extracted="$temporary_directory/extracted"
mkdir "$extracted"

printf '%s\n' "Downloading WarSampo Knowledge Graph 2.0.0 from Zenodo..."
curl --fail --location --retry 3 --show-error "$archive_url" --output "$archive"

actual_md5=$(calculate_md5 "$archive")
[ "$actual_md5" = "$archive_md5" ] || fail "archive checksum mismatch: expected $archive_md5, got $actual_md5"

printf '%s\n' "Checksum verified; extracting 61 Turtle modules..."
unzip -q "$archive" -d "$extracted"

ttl_file_count=$(find "$extracted" -type f -name '*.ttl' -print | wc -l | tr -d '[:space:]')
[ "$ttl_file_count" = "$expected_ttl_files" ] || fail "unexpected Turtle module count: expected $expected_ttl_files, got $ttl_file_count"

printf '%s\n' "Applying the reviewed invalid-date correction..."
patch --batch --forward -p1 -d "$extracted" < "$correction_patch"

mv "$extracted" "$destination"

printf '%s\n' "WarSampo is ready at $destination ($ttl_file_count Turtle modules)."
printf '%s\n' "Run ./scripts/linter.sh validate --data warsampo --profile warsampo to validate it."
