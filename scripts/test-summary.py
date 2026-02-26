#!/usr/bin/env python3
"""Aggregate JUnit XML test results into a summary table.

Usage:
    python3 scripts/test-summary.py [ROOT_DIR] [--format text|markdown]

Discovers JUnit XML files produced by Gradle, pytest, and Vitest, then prints
a per-component breakdown by test type (unit / integration / e2e).
"""
from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

# Pattern → (component extraction strategy, test type)
#   component is derived from the path segment before /build/ for Gradle,
#   or a fixed name for pytest/vitest.
PATTERNS = [
    # Gradle / local layout
    ("**/build/test-results/test/**/*.xml", "unit"),
    ("**/build/test-results/integrationTest/**/*.xml", "integration"),
    ("**/build/test-results/end2EndTest/**/*.xml", "e2e"),
    ("**/risk-engine/**/pytest.xml", "unit"),
    ("**/ui/**/junit.xml", "unit"),
    # CI artifact layout (download-artifact@v4 strips leading directory structure)
    ("**/integration-test-xml-*/*.xml", "integration"),
    ("**/e2e-test-xml/*.xml", "e2e"),
    ("**/python-test-xml/pytest.xml", "unit"),
    ("**/ui-test-xml/junit.xml", "unit"),
]

TEST_TYPES = ["unit", "integration", "e2e"]
COLUMN_WIDTH = 12


def discover_xml_files(root: Path) -> list[tuple[str, str, Path]]:
    """Return a list of (component, test_type, xml_path) tuples."""
    results = []
    seen: set[Path] = set()
    for pattern, test_type in PATTERNS:
        for xml_path in sorted(root.glob(pattern)):
            if xml_path in seen:
                continue
            seen.add(xml_path)
            component = _extract_component(xml_path, root, test_type)
            if component:
                results.append((component, test_type, xml_path))
    return results


def _extract_component(xml_path: Path, root: Path, test_type: str) -> str | None:
    rel = xml_path.relative_to(root)
    parts = rel.parts

    # Gradle: <component>/build/test-results/<task>/...
    if "build" in parts:
        idx = parts.index("build")
        if idx > 0:
            return parts[idx - 1]
        return None

    # CI artifact: integration-test-xml-<module>/...
    for part in parts:
        if part.startswith("integration-test-xml-"):
            return part.removeprefix("integration-test-xml-")

    # CI artifact: e2e-test-xml/...
    if "e2e-test-xml" in parts:
        return "acceptance-tests"

    # pytest: risk-engine/**/pytest.xml or python-test-xml/pytest.xml
    if xml_path.name == "pytest.xml":
        if "risk-engine" in parts or "python-test-xml" in parts:
            return "risk-engine"

    # vitest: ui/**/junit.xml or ui-test-xml/junit.xml
    if xml_path.name == "junit.xml":
        if "ui" in parts or "ui-test-xml" in parts:
            return "ui"

    return None


def parse_results(
    discovered: list[tuple[str, str, Path]],
) -> dict[tuple[str, str], dict[str, int]]:
    """Parse XML files and aggregate counts by (component, test_type)."""
    summary: dict[tuple[str, str], dict[str, int]] = defaultdict(
        lambda: {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    )

    for component, test_type, xml_path in discovered:
        key = (component, test_type)
        try:
            tree = ET.parse(xml_path)
        except ET.ParseError:
            continue
        root = tree.getroot()
        for ts in _iter_testsuites(root):
            summary[key]["tests"] += int(ts.get("tests", 0))
            summary[key]["failures"] += int(ts.get("failures", 0))
            summary[key]["errors"] += int(ts.get("errors", 0))
            summary[key]["skipped"] += int(ts.get("skipped", 0))

    return dict(summary)


def _iter_testsuites(root):
    """Yield <testsuite> elements regardless of whether the root is <testsuite> or <testsuites>."""
    if root.tag == "testsuite":
        yield root
    elif root.tag == "testsuites":
        yield from root.iter("testsuite")


def _components_sorted(summary):
    return sorted({comp for comp, _ in summary})


def format_text(summary: dict[tuple[str, str], dict[str, int]]) -> str:
    if not summary:
        return "No test results found."

    components = _components_sorted(summary)
    col_w = COLUMN_WIDTH
    name_w = max(len(c) for c in components + ["Component"]) + 2
    line_w = name_w + col_w * len(TEST_TYPES) + col_w  # +col_w for Total

    lines = []
    lines.append("Test Summary")
    lines.append("\u2550" * line_w)
    header = f"{'Component':<{name_w}}" + "".join(f"{t.title():>{col_w}}" for t in TEST_TYPES) + f"{'Total':>{col_w}}"
    lines.append(header)
    lines.append("\u2500" * line_w)

    grand = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}

    for comp in components:
        row_total = 0
        cells = []
        for tt in TEST_TYPES:
            key = (comp, tt)
            if key in summary:
                count = summary[key]["tests"]
                cells.append(f"{count:>{col_w}}")
                row_total += count
            else:
                cells.append(f"{'-':>{col_w}}")
        cells.append(f"{row_total:>{col_w}}")
        lines.append(f"{comp:<{name_w}}" + "".join(cells))

        for key_fields in [(comp, tt) for tt in TEST_TYPES]:
            if key_fields in summary:
                for k in grand:
                    grand[k] += summary[key_fields][k]

    lines.append("\u2500" * line_w)
    total_cells = []
    for tt in TEST_TYPES:
        tt_total = sum(summary.get((c, tt), {}).get("tests", 0) for c in components)
        total_cells.append(f"{tt_total:>{col_w}}" if tt_total else f"{'-':>{col_w}}")
    total_cells.append(f"{grand['tests']:>{col_w}}")
    lines.append(f"{'Total':<{name_w}}" + "".join(total_cells))

    passed = grand["tests"] - grand["failures"] - grand["errors"] - grand["skipped"]
    failed = grand["failures"] + grand["errors"]
    skipped = grand["skipped"]

    lines.append("")
    if failed:
        lines.append(f"\u2717 {passed} passed, {failed} failed, {skipped} skipped")
    else:
        lines.append(f"\u2713 {passed} passed, {failed} failed, {skipped} skipped")

    return "\n".join(lines)


def format_markdown(summary: dict[tuple[str, str], dict[str, int]]) -> str:
    if not summary:
        return "No test results found."

    components = _components_sorted(summary)

    lines = []
    lines.append("## Test Summary")
    lines.append("")
    lines.append("| Component | Unit | Integration | E2E | Total |")
    lines.append("|-----------|-----:|------------:|----:|------:|")

    grand = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}

    for comp in components:
        row_total = 0
        cells = []
        for tt in TEST_TYPES:
            key = (comp, tt)
            if key in summary:
                count = summary[key]["tests"]
                cells.append(str(count))
                row_total += count
            else:
                cells.append("-")
        lines.append(f"| {comp} | {cells[0]} | {cells[1]} | {cells[2]} | {row_total} |")

        for tt in TEST_TYPES:
            if (comp, tt) in summary:
                for k in grand:
                    grand[k] += summary[(comp, tt)][k]

    total_cells = []
    for tt in TEST_TYPES:
        tt_total = sum(summary.get((c, tt), {}).get("tests", 0) for c in components)
        total_cells.append(str(tt_total) if tt_total else "-")
    lines.append(f"| **Total** | **{total_cells[0]}** | **{total_cells[1]}** | **{total_cells[2]}** | **{grand['tests']}** |")

    passed = grand["tests"] - grand["failures"] - grand["errors"] - grand["skipped"]
    failed = grand["failures"] + grand["errors"]
    skipped = grand["skipped"]

    lines.append("")
    if failed:
        lines.append(f"\u2717 **{passed} passed, {failed} failed, {skipped} skipped**")
    else:
        lines.append(f"\u2713 **{passed} passed, {failed} failed, {skipped} skipped**")

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Aggregate JUnit XML test results.")
    parser.add_argument("root", nargs="?", default=".", help="Root directory to scan (default: .)")
    parser.add_argument("--format", choices=["text", "markdown"], default="text", dest="fmt")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    discovered = discover_xml_files(root)
    summary = parse_results(discovered)

    if args.fmt == "markdown":
        print(format_markdown(summary))
    else:
        print(format_text(summary))


if __name__ == "__main__":
    main()
