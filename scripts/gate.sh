#!/bin/bash
# The loop's commit gate: full suite, then scorecard ratchets. Exits nonzero on any failure.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew test --rerun 2>&1 | tee /tmp/test-output.txt | grep -E "BUILD (SUCCESSFUL|FAILED)"
grep -q "BUILD SUCCESSFUL" /tmp/test-output.txt
./gradlew compileJava --rerun-tasks 2>&1 | tee /tmp/compile-warnings.txt | grep -E "BUILD (SUCCESSFUL|FAILED)"
grep -q "BUILD SUCCESSFUL" /tmp/compile-warnings.txt
./gradlew pmdMain > /dev/null
python3 scripts/scorecard.py
