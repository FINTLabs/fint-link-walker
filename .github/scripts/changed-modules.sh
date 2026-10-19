#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/paths.sh"

base="${1:-}"
head="${2:-HEAD}"

if [ -z "$base" ] || ! git cat-file -e "$base^{commit}" 2>/dev/null; then
  echo "scanner=true"
  echo "reader=true"
  exit 0
fi

changed_files=$(git diff --name-only "$base" "$head")

touches() {
  for path in $1; do
    if echo "$changed_files" | grep -q "^$path\(/\|$\)"; then
      return 0
    fi
  done
  return 1
}

echo "scanner=$(touches "$SCANNER_PATHS" && echo true || echo false)"
echo "reader=$(touches "$READER_PATHS" && echo true || echo false)"
