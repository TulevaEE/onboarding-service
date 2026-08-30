#!/usr/bin/env bash
# Runs mutation testing for one top-level module and records the slice.
# Usage: scripts/pitest-slice.sh <module>   e.g. scripts/pitest-slice.sh deadline
#        scripts/pitest-slice.sh root       covers classes directly in ee.tuleva.onboarding
set -euo pipefail
MODULE="${1:?usage: pitest-slice.sh <module>}"
cd "$(dirname "$0")/.."

if [ "$MODULE" = "root" ]; then
  TARGET=$(find src/main/java/ee/tuleva/onboarding -maxdepth 1 -name '*.java' ! -name 'package-info.java' \
    -exec basename {} .java \; | sed 's/^/ee.tuleva.onboarding./' | paste -sd, -)
  RECORD_AS="(root)"
else
  TARGET="ee.tuleva.onboarding.${MODULE}.*"
  RECORD_AS="$MODULE"
fi

./gradlew pitest -Ppitest.target="$TARGET"
python3 scripts/pitest_slice_record.py "$RECORD_AS"
