#!/usr/bin/env python3
"""Aggregates build reports into metrics/scorecard.json and enforces the refactoring ratchets.

Usage:
  python3 scripts/scorecard.py          # emit scorecard, fail on any ratchet regression vs HEAD
  python3 scripts/scorecard.py --init   # emit scorecard without comparing (first baseline)

Inputs (produced by ./gradlew test pmdMain, plus optional pitest):
  build/metrics/modulith.json           ModuleMetricsTest emitter
  build/reports/pmd/main.xml            PMD complexity/cohesion ruleset
  build/reports/jacoco/test/jacocoTestReport.xml
  build/reports/pitest/mutations.xml    optional, full runs only
  src/main/java, src/test/groovy        grep-based counts

Ratchets: LOWER_IS_BETTER must never increase, HIGHER_IS_BETTER must never
decrease (coverage gets a small tolerance so deleting well-covered dead code
is not blocked). Everything else is informational trend data.
"""

import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCORECARD = ROOT / "metrics" / "scorecard.json"

LOWER_IS_BETTER = [
    "classCouplingViolations",
    "clockViolations",
    "cognitiveComplexityViolations",
    "deepNestingViolations",
    "compilerWarnings",
    "disabledTests",
    "longClasses",
    "godClasses",
    "modulithViolations",
    "moduleCycles",
    "pmdViolations",
    "unmarkedPackages",
    "springBootTests",
    "top15ClassLines",
]
HIGHER_IS_BETTER = ["lineCoverage", "branchCoverage"]
COVERAGE_TOLERANCE = 0.2


def modulith_metrics():
    path = ROOT / "build" / "metrics" / "modulith.json"
    data = json.loads(path.read_text())
    instabilities = [m["instability"] for m in data["modules"].values()]
    return {
        "modulithViolations": data["violationCount"],
        "moduleCycles": data["cycleCount"],
        "meanInstability": round(sum(instabilities) / len(instabilities), 3),
    }


def pmd_metrics():
    path = ROOT / "build" / "reports" / "pmd" / "main.xml"
    tree = ET.parse(path)
    ns = {"pmd": "http://pmd.sourceforge.net/report/2.0.0"}
    violations = tree.getroot().findall(".//pmd:violation", ns)
    by_rule = {}
    for violation in violations:
        rule = violation.get("rule")
        by_rule[rule] = by_rule.get(rule, 0) + 1
    return {
        "pmdViolations": len(violations),
        "godClasses": by_rule.get("GodClass", 0),
        "classCouplingViolations": by_rule.get("CouplingBetweenObjects", 0),
        "cognitiveComplexityViolations": by_rule.get("CognitiveComplexity", 0),
        "deepNestingViolations": by_rule.get("AvoidDeeplyNestedIfStmts", 0),
        "pmdViolationsByRule": dict(sorted(by_rule.items())),
    }


def jacoco_metrics():
    path = ROOT / "build" / "reports" / "jacoco" / "test" / "jacocoTestReport.xml"
    tree = ET.parse(path)
    counters = {c.get("type"): c for c in tree.getroot() if c.tag == "counter"}
    result = {}
    for counter_type, key in [("LINE", "lineCoverage"), ("BRANCH", "branchCoverage")]:
        counter = counters[counter_type]
        covered, missed = int(counter.get("covered")), int(counter.get("missed"))
        result[key] = round(100.0 * covered / (covered + missed), 2)
    return result


def pitest_metrics():
    path = ROOT / "build" / "reports" / "pitest" / "mutations.xml"
    if not path.exists():
        return {}
    root = ET.parse(path).getroot()
    mutations = root.findall("mutation")
    # Scoped per-iteration runs (and the generated-code exclusions) mark the report
    # partial; only a full-codebase run (thousands of mutants) is valid trend data.
    if len(mutations) < 5000:
        return {}
    detected = sum(1 for m in mutations if m.get("detected") == "true")
    return {
        "mutationScore": round(100.0 * detected / len(mutations), 2) if mutations else None,
        "mutationsTotal": len(mutations),
    }


def source_metrics():
    main = ROOT / "src" / "main" / "java"
    packages = {f.parent for f in main.rglob("*.java")}
    marked = {
        f.parent
        for f in main.rglob("package-info.java")
        if "@NullMarked" in f.read_text(encoding="utf-8")
    }
    spring_boot_tests = subprocess.run(
        ["grep", "-rl", "@SpringBootTest", str(ROOT / "src" / "test" / "groovy")],
        capture_output=True,
        text=True,
    ).stdout.splitlines()
    class_sizes = sorted(
        (sum(1 for _ in f.open(encoding="utf-8")) for f in main.rglob("*.java")), reverse=True
    )
    return {
        "unmarkedPackages": len(packages - marked),
        "totalPackages": len(packages),
        "springBootTests": len(spring_boot_tests),
        "top15ClassLines": sum(class_sizes[:15]),
    }


def convention_metrics():
    main = ROOT / "src" / "main" / "java"
    clock_pattern = re.compile(r"\b(Instant|LocalDate|LocalDateTime|ZonedDateTime|LocalTime|OffsetDateTime)\.now\(\)")
    clock_violations = 0
    for f in main.rglob("*.java"):
        if f.name in ("ClockHolder.java", "ClockConfig.java"):
            continue
        clock_violations += len(clock_pattern.findall(f.read_text(encoding="utf-8")))
    disabled = subprocess.run(
        ["grep", "-rlE", "@Disabled|@Ignore\b", str(ROOT / "src" / "test" / "groovy")],
        capture_output=True,
        text=True,
    ).stdout.splitlines()
    slow_tests = []
    results = ROOT / "build" / "test-results" / "test"
    if results.exists():
        import xml.etree.ElementTree as ET2
        for f in results.glob("*.xml"):
            try:
                root = ET2.parse(f).getroot()
                slow_tests.append((float(root.get("time", 0)), root.get("name", f.name)))
            except ET2.ParseError:
                continue
    slow_tests.sort(reverse=True)
    wall_time = None
    log = Path("/tmp/test-output.txt")
    if log.exists():
        for line in log.open(encoding="utf-8"):
            match = re.search(r"BUILD SUCCESSFUL in (?:(\d+)m )?(\d+)s", line)
            if match:
                wall_time = int(match.group(1) or 0) * 60 + int(match.group(2))
    long_classes = sum(
        1
        for f in main.rglob("*.java")
        if sum(1 for _ in f.open(encoding="utf-8")) > 500
    )
    return {
        "clockViolations": clock_violations,
        "longClasses": long_classes,
        "disabledTests": len(disabled),
        "suiteWallTimeSeconds": wall_time,
        "slowest10TestClassesSeconds": round(sum(s for s, _ in slow_tests[:10]), 1),
        "testClassesOver10Seconds": sum(1 for s, _ in slow_tests if s > 10),
    }


def compiler_warning_metrics():
    log = Path("/tmp/compile-warnings.txt")
    if not log.exists():
        return {}
    count = sum(1 for line in log.open(encoding="utf-8") if ": warning:" in line)
    return {"compilerWarnings": count}


def previous_scorecard():
    result = subprocess.run(
        ["git", "-C", str(ROOT), "show", "HEAD:metrics/scorecard.json"],
        capture_output=True,
        text=True,
    )
    return json.loads(result.stdout) if result.returncode == 0 else None


def compare(current, previous):
    regressions = []
    for key in LOWER_IS_BETTER:
        if key in previous and current[key] > previous[key]:
            regressions.append(f"{key}: {previous[key]} -> {current[key]} (must not increase)")
    for key in HIGHER_IS_BETTER:
        if key in previous and current[key] < previous[key] - COVERAGE_TOLERANCE:
            regressions.append(f"{key}: {previous[key]} -> {current[key]} (must not decrease)")
    return regressions


def main():
    current = {}
    for collect in [
        modulith_metrics,
        pmd_metrics,
        jacoco_metrics,
        pitest_metrics,
        source_metrics,
        convention_metrics,
        compiler_warning_metrics,
    ]:
        current.update(collect())

    SCORECARD.parent.mkdir(exist_ok=True)
    previous = previous_scorecard()

    if previous and "mutationScore" not in current:
        for key in ("mutationScore", "mutationsTotal"):
            if key in previous:
                current[key] = previous[key]

    if previous and "compilerWarnings" not in current and "compilerWarnings" in previous:
        current["compilerWarnings"] = previous["compilerWarnings"]

    if previous and "--init" not in sys.argv:
        for key in sorted(set(previous) | set(current)):
            before, after = previous.get(key), current.get(key)
            marker = "" if before == after else "  *"
            if not isinstance(before, dict):
                print(f"{key:24} {before} -> {after}{marker}")
        regressions = compare(current, previous)
        if regressions:
            print("\nRATCHET REGRESSIONS:")
            for regression in regressions:
                print(f"  {regression}")
            sys.exit(1)
    else:
        for key, value in sorted(current.items()):
            if not isinstance(value, dict):
                print(f"{key:24} {value}")

    SCORECARD.write_text(json.dumps(current, indent=2, sort_keys=True) + "\n")
    print(f"\nScorecard written: {SCORECARD.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
