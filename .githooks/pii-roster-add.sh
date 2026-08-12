#!/bin/bash
# Adds a name to the PII roster as a salted hash, so the roster never holds
# the name itself. Pass the name exactly as it would be written in code.
set -euo pipefail
ROSTER="$(dirname "${BASH_SOURCE[0]}")/pii-roster.sha256"
[ $# -eq 1 ] || { echo "usage: $0 'Eesnimi Perekonnanimi'" >&2; exit 1; }
SALT=$(grep '^salt=' "$ROSTER" | cut -d= -f2)
NORMALISED=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')
if command -v sha256sum >/dev/null 2>&1; then
  HASH=$(printf '%s' "${SALT}${NORMALISED}" | sha256sum | cut -d' ' -f1)
else
  HASH=$(printf '%s' "${SALT}${NORMALISED}" | shasum -a 256 | cut -d' ' -f1)
fi
grep -q "^${HASH}$" "$ROSTER" && { echo "Already on the roster."; exit 0; }
echo "$HASH" >> "$ROSTER"
echo "Added. The roster now covers $(grep -cE '^[0-9a-f]{64}$' "$ROSTER") names."
