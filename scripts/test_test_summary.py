"""Tests for test-summary.py — verifies XML parsing, aggregation, and output formatting."""
import importlib.util
import textwrap
from pathlib import Path

import pytest

# test-summary.py has a hyphen so we load it by path
_spec = importlib.util.spec_from_file_location(
    "test_summary", Path(__file__).with_name("test-summary.py")
)
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)

discover_xml_files = _mod.discover_xml_files
parse_results = _mod.parse_results
format_text = _mod.format_text
format_markdown = _mod.format_markdown


@pytest.fixture
def tmp_repo(tmp_path):
    """Create a fake repo layout with JUnit XML files."""
    return tmp_path


def _write_xml(path, tests=10, failures=0, errors=0, skipped=0):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(f"""\
        <?xml version="1.0" encoding="UTF-8"?>
        <testsuite name="com.example.SomeTest" tests="{tests}" failures="{failures}" errors="{errors}" skipped="{skipped}" time="1.23">
          <testcase classname="com.example.SomeTest" name="test1" time="0.5"/>
        </testsuite>
    """))


class TestDiscoverXmlFiles:
    def test_discovers_kotlin_unit_tests(self, tmp_repo):
        xml = tmp_repo / "price-service" / "build" / "test-results" / "test" / "TEST-Some.xml"
        _write_xml(xml)
        results = discover_xml_files(tmp_repo)
        assert ("price-service", "unit") in [(r[0], r[1]) for r in results]

    def test_discovers_kotlin_integration_tests(self, tmp_repo):
        xml = tmp_repo / "position-service" / "build" / "test-results" / "integrationTest" / "TEST-Some.xml"
        _write_xml(xml)
        results = discover_xml_files(tmp_repo)
        assert ("position-service", "integration") in [(r[0], r[1]) for r in results]

    def test_discovers_kotlin_acceptance_tests(self, tmp_repo):
        xml = tmp_repo / "acceptance-tests" / "build" / "test-results" / "end2EndTest" / "TEST-Some.xml"
        _write_xml(xml)
        results = discover_xml_files(tmp_repo)
        assert ("acceptance-tests", "e2e") in [(r[0], r[1]) for r in results]

    def test_discovers_risk_engine_pytest(self, tmp_repo):
        xml = tmp_repo / "risk-engine" / "reports" / "pytest.xml"
        _write_xml(xml)
        results = discover_xml_files(tmp_repo)
        assert ("risk-engine", "unit") in [(r[0], r[1]) for r in results]

    def test_discovers_ui_junit(self, tmp_repo):
        xml = tmp_repo / "ui" / "test-results" / "junit.xml"
        _write_xml(xml)
        results = discover_xml_files(tmp_repo)
        assert ("ui", "unit") in [(r[0], r[1]) for r in results]

    def test_returns_empty_for_no_xml(self, tmp_repo):
        results = discover_xml_files(tmp_repo)
        assert results == []

    def test_discovers_multiple_xml_per_component(self, tmp_repo):
        _write_xml(tmp_repo / "gateway" / "build" / "test-results" / "test" / "TEST-A.xml", tests=5)
        _write_xml(tmp_repo / "gateway" / "build" / "test-results" / "test" / "TEST-B.xml", tests=3)
        results = discover_xml_files(tmp_repo)
        gateway_results = [r for r in results if r[0] == "gateway"]
        assert len(gateway_results) == 2


class TestParseResults:
    def test_parses_counts_from_xml(self, tmp_repo):
        xml = tmp_repo / "common" / "build" / "test-results" / "test" / "TEST-X.xml"
        _write_xml(xml, tests=12, failures=1, errors=0, skipped=2)
        discovered = discover_xml_files(tmp_repo)
        summary = parse_results(discovered)
        assert summary[("common", "unit")]["tests"] == 12
        assert summary[("common", "unit")]["failures"] == 1
        assert summary[("common", "unit")]["skipped"] == 2

    def test_aggregates_multiple_xml_files(self, tmp_repo):
        _write_xml(tmp_repo / "gateway" / "build" / "test-results" / "test" / "TEST-A.xml", tests=5, failures=1)
        _write_xml(tmp_repo / "gateway" / "build" / "test-results" / "test" / "TEST-B.xml", tests=3, failures=0)
        discovered = discover_xml_files(tmp_repo)
        summary = parse_results(discovered)
        assert summary[("gateway", "unit")]["tests"] == 8
        assert summary[("gateway", "unit")]["failures"] == 1

    def test_separates_unit_and_integration(self, tmp_repo):
        _write_xml(tmp_repo / "price-service" / "build" / "test-results" / "test" / "TEST-A.xml", tests=42)
        _write_xml(tmp_repo / "price-service" / "build" / "test-results" / "integrationTest" / "TEST-B.xml", tests=12)
        discovered = discover_xml_files(tmp_repo)
        summary = parse_results(discovered)
        assert summary[("price-service", "unit")]["tests"] == 42
        assert summary[("price-service", "integration")]["tests"] == 12


class TestFormatText:
    def test_produces_table_with_totals(self, tmp_repo):
        _write_xml(tmp_repo / "common" / "build" / "test-results" / "test" / "TEST-X.xml", tests=12)
        _write_xml(tmp_repo / "ui" / "test-results" / "junit.xml", tests=276)
        discovered = discover_xml_files(tmp_repo)
        summary = parse_results(discovered)
        output = format_text(summary)
        assert "common" in output
        assert "ui" in output
        assert "12" in output
        assert "276" in output
        assert "288" in output  # total
        assert "288 passed" in output

    def test_shows_dash_for_missing_test_type(self, tmp_repo):
        _write_xml(tmp_repo / "common" / "build" / "test-results" / "test" / "TEST-X.xml", tests=12)
        discovered = discover_xml_files(tmp_repo)
        summary = parse_results(discovered)
        output = format_text(summary)
        # common has no integration or acceptance tests, so those columns should show '-'
        lines = output.splitlines()
        common_line = [l for l in lines if "common" in l][0]
        assert "-" in common_line

    def test_shows_failures_in_footer(self, tmp_repo):
        _write_xml(tmp_repo / "common" / "build" / "test-results" / "test" / "TEST-X.xml", tests=12, failures=2, skipped=1)
        discovered = discover_xml_files(tmp_repo)
        summary = parse_results(discovered)
        output = format_text(summary)
        assert "9 passed" in output
        assert "2 failed" in output
        assert "1 skipped" in output

    def test_empty_summary_produces_no_data_message(self):
        output = format_text({})
        assert "No test results found" in output


class TestFormatMarkdown:
    def test_produces_markdown_table(self, tmp_repo):
        _write_xml(tmp_repo / "common" / "build" / "test-results" / "test" / "TEST-X.xml", tests=12)
        _write_xml(tmp_repo / "risk-engine" / "reports" / "pytest.xml", tests=235)
        discovered = discover_xml_files(tmp_repo)
        summary = parse_results(discovered)
        output = format_markdown(summary)
        assert "| Component" in output
        assert "| common" in output
        assert "| risk-engine" in output
        assert "| **Total**" in output

    def test_empty_summary_produces_no_data_message(self):
        output = format_markdown({})
        assert "No test results found" in output


class TestDiscoverXmlFilesFromCIArtifacts:
    """After download-artifact@v4, XML files live under artifact-name dirs
    with leading directory structure stripped."""

    def test_discovers_integration_tests_from_ci_artifact(self, tmp_repo):
        xml = tmp_repo / "integration-test-xml-position-service" / "TEST-Some.xml"
        _write_xml(xml)
        results = discover_xml_files(tmp_repo)
        assert ("position-service", "integration") in [(r[0], r[1]) for r in results]

    def test_discovers_acceptance_tests_from_ci_artifact(self, tmp_repo):
        xml = tmp_repo / "e2e-test-xml" / "TEST-Some.xml"
        _write_xml(xml)
        results = discover_xml_files(tmp_repo)
        assert ("acceptance-tests", "e2e") in [(r[0], r[1]) for r in results]

    def test_discovers_python_tests_from_ci_artifact(self, tmp_repo):
        xml = tmp_repo / "python-test-xml" / "pytest.xml"
        _write_xml(xml)
        results = discover_xml_files(tmp_repo)
        assert ("risk-engine", "unit") in [(r[0], r[1]) for r in results]

    def test_discovers_ui_tests_from_ci_artifact(self, tmp_repo):
        xml = tmp_repo / "ui-test-xml" / "junit.xml"
        _write_xml(xml)
        results = discover_xml_files(tmp_repo)
        assert ("ui", "unit") in [(r[0], r[1]) for r in results]

    def test_no_double_counting_when_both_layouts_present(self, tmp_repo):
        """If a file matches both Gradle and CI patterns, it should only be counted once."""
        xml = tmp_repo / "kotlin-unit-test-xml" / "gateway" / "build" / "test-results" / "test" / "TEST-A.xml"
        _write_xml(xml, tests=5)
        results = discover_xml_files(tmp_repo)
        gateway_results = [r for r in results if r[0] == "gateway" and r[1] == "unit"]
        assert len(gateway_results) == 1


class TestParseResultsWithNestedTestsuites:
    """JUnit XML from pytest wraps testsuites inside a <testsuites> root."""

    def test_parses_nested_testsuite_elements(self, tmp_repo):
        xml_path = tmp_repo / "risk-engine" / "reports" / "pytest.xml"
        xml_path.parent.mkdir(parents=True, exist_ok=True)
        xml_path.write_text(textwrap.dedent("""\
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuites>
              <testsuite name="suite1" tests="100" failures="0" errors="0" skipped="5" time="1.0"/>
              <testsuite name="suite2" tests="135" failures="2" errors="1" skipped="0" time="2.0"/>
            </testsuites>
        """))
        discovered = discover_xml_files(tmp_repo)
        summary = parse_results(discovered)
        assert summary[("risk-engine", "unit")]["tests"] == 235
        assert summary[("risk-engine", "unit")]["failures"] == 2
        assert summary[("risk-engine", "unit")]["errors"] == 1
        assert summary[("risk-engine", "unit")]["skipped"] == 5
