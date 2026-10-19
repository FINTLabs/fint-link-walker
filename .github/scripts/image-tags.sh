#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/paths.sh"

# The tag is a hash of the git trees an image is built from, so the same source always maps to
# the same tag: a commit that only touches kustomize or docs reuses the image in the registry,
# and the squash commit on main reuses the image develop built.
source_hash() {
  git ls-tree HEAD -- $1 | git hash-object --stdin | cut -c1-12
}

echo "scanner=src-$(source_hash "$SCANNER_PATHS")"
echo "reader=src-$(source_hash "$READER_PATHS")"
