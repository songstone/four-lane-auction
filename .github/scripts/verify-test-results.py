"""Verify Gradle JUnit reports; a successful build must execute MySQL tests."""

import argparse
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


TASKS = {"test": "UTC", "testSeoul": "Asia/Seoul"}
MYSQL_SUITES = {
    "com.fourlane.global.persistence.SchemaAndTimeIntegrationTest",
    "com.fourlane.auction.session.SessionHttpAndStompIntegrationTest",
}
COUNTERS = ("tests", "failures", "errors", "skipped")


def read_suite(report):
    suite = ET.parse(report).getroot()
    if suite.tag != "testsuite":
        raise ValueError("expected a Gradle testsuite element")
    name = suite.attrib["name"]
    counts = {key: int(suite.attrib[key]) for key in COUNTERS}
    if any(value < 0 for value in counts.values()):
        raise ValueError("negative test count")
    cases = suite.findall("testcase")
    actual = {"tests": len(cases)}
    actual.update({
        key: sum(case.find(element) is not None for case in cases)
        for key, element in (("failures", "failure"), ("errors", "error"), ("skipped", "skipped"))
    })
    if counts != actual:
        raise ValueError(f"suite counts {counts} disagree with testcase elements {actual}")
    if sum(counts[key] for key in ("failures", "errors", "skipped")) > counts["tests"]:
        raise ValueError("test outcome counts exceed the total")
    return name, counts


def verify(backend):
    lines = [
        "## Backend test execution",
        "",
        "Executed = tests - skipped. MySQL rows require Testcontainers; counts are per JVM run.",
        "",
        "| Task / JVM zone | Suite | Tests | Executed | Failures | Errors | Skipped |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    problems = []
    for task, zone in TASKS.items():
        totals = dict.fromkeys(COUNTERS, 0)
        mysql_executed = 0
        found_mysql = set()
        reports = sorted(backend.glob(f"*/build/test-results/{task}/TEST-*.xml"))
        if not reports:
            problems.append(f"{task}: no JUnit XML reports")
        for report in reports:
            try:
                name, counts = read_suite(report)
            except (ET.ParseError, KeyError, ValueError, OSError) as error:
                problems.append(f"{report.relative_to(backend)}: {error}")
                continue
            executed = counts["tests"] - counts["skipped"]
            is_mysql = name in MYSQL_SUITES and report.parent == backend / "app/build/test-results" / task
            label = f"{name} (Testcontainers MySQL)" if is_mysql else name
            lines.append(
                f"| {task} / {zone} | {label} | {counts['tests']} | {executed} | "
                f"{counts['failures']} | {counts['errors']} | {counts['skipped']} |"
            )
            for key in COUNTERS:
                totals[key] += counts[key]
            if counts["failures"] or counts["errors"] or counts["skipped"]:
                problems.append(f"{task}/{name}: failures, errors and skipped must all be zero")
            if is_mysql:
                found_mysql.add(name)
                mysql_executed += executed
                if executed == 0:
                    problems.append(f"{task}/{name}: no MySQL tests executed")
        for missing in sorted(MYSQL_SUITES - found_mysql):
            problems.append(f"{task}: missing required MySQL suite {missing}")
        executed = totals["tests"] - totals["skipped"]
        if executed == 0:
            problems.append(f"{task}: no tests executed")
        lines.append(
            f"| {task} / {zone} | **Total (MySQL executed: {mysql_executed})** | "
            f"{totals['tests']} | {executed} | {totals['failures']} | {totals['errors']} | {totals['skipped']} |"
        )
    lines.extend(["", f"Result: **{'FAIL' if problems else 'PASS'}**"])
    if problems:
        lines.extend(["", *[f"- {problem}" for problem in problems]])
    return "\n".join(lines) + "\n", problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--backend-dir", type=Path, default=Path(__file__).resolve().parents[2] / "backend"
    )
    args = parser.parse_args()
    summary, problems = verify(args.backend_dir.resolve())
    print(summary, end="")
    if summary_path := os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(summary_path).open("a", encoding="utf-8") as output:
            output.write(summary)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
