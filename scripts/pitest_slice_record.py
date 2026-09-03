#!/usr/bin/env python3
"""Records the latest pitest run into metrics/pitest-slices.json, per module.

Usage:
  python3 scripts/pitest_slice_record.py <module>   # after ./gradlew pitest -Ppitest.target=...

Reads build/reports/pitest/mutations.xml, attributes every mutation to its
top-level module (the package segment after ee.tuleva.onboarding, or "(root)"
for root-package classes), and overwrites the slice entry for each module
present in the report. An empty report records a zero-mutant slice for the
requested module so the rotation can still complete. Modules not in the
report keep their previous slice. Mutation scores decompose by class, so the
mutation-weighted aggregate over complete slices equals a full run.
"""

import json
import sys
import xml.etree.ElementTree as ET
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPORT = ROOT / "build" / "reports" / "pitest" / "mutations.xml"
SLICES = ROOT / "metrics" / "pitest-slices.json"
PREFIX = "ee.tuleva.onboarding."


def module_of(mutated_class):
    rest = mutated_class.removeprefix(PREFIX)
    head = rest.split(".")[0]
    return head if "." in rest else "(root)"


def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: pitest_slice_record.py <module>")
    requested = sys.argv[1]

    if not REPORT.exists():
        raise SystemExit(f"No pitest report at {REPORT} - run ./gradlew pitest first")

    per_module = {}
    for mutation in ET.parse(REPORT).getroot().findall("mutation"):
        module = module_of(mutation.findtext("mutatedClass"))
        entry = per_module.setdefault(module, {"mutations": 0, "detected": 0})
        entry["mutations"] += 1
        if mutation.get("detected") == "true":
            entry["detected"] += 1

    if not per_module:
        per_module = {requested: {"mutations": 0, "detected": 0}}

    slices = json.loads(SLICES.read_text()) if SLICES.exists() else {}
    for module, entry in per_module.items():
        score = (
            round(100.0 * entry["detected"] / entry["mutations"], 2)
            if entry["mutations"]
            else None
        )
        slices[module] = {
            "mutations": entry["mutations"],
            "detected": entry["detected"],
            "score": score,
            "recordedAt": date.today().isoformat(),
        }

    SLICES.write_text(json.dumps(dict(sorted(slices.items())), indent=2) + "\n")
    for module in sorted(per_module):
        recorded = slices[module]
        print(f"{module}: {recorded['score']}% ({recorded['detected']}/{recorded['mutations']})")


if __name__ == "__main__":
    main()
