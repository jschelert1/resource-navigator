package com.jschelert.resourcenavigator

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jschelert.resourcenavigator.inspection.MissingResourceInspection

/**
 * IntelliJ Platform fixture tests for Resource Navigator's missing-resource inspection.
 *
 * These tests exercise Resource Navigator against real Python PSI in the PyCharm
 * test platform rather than testing utility functions in isolation.
 *
 * Regression Coverage
 * -------------------
 *
 * The shared Python test-data corpus covers the no-warning inspection behavior
 * associated with GitHub Issues #1 through #8:
 *
 * • #1/#2 — Generic type annotations must not be interpreted as resources.
 * • #3/#6 — Escaped backslash literals must not become resources.
 * • #4    — Unquoted resource-looking prose must remain ignored.
 * • #5    — Forward and outside-call pseudo-keyword references must fail closed.
 * • #7    — Dynamic pathlib.Path composition must fail closed.
 * • #8    — Simple variable references in descriptive f-string prose must be ignored.
 * • #9    — CLI switches, escape fragments, and documentation/example paths must not warn.
 * • #10   — Quoted paths in Python docstrings are documentation and must not warn.
 * • #11   — Regex character classes must not become bracketed resource citations.
 *
 * Issue #8 is split across the Python corpus and this Kotlin fixture. Its no-warning
 * behavior remains in the shared .py test data, while its positive missing-resource
 * behavior is asserted directly here in Kotlin because IntelliJ expected-highlighting
 * markup in the .py file cannot reliably evaluate the RN warning on the resolved path.
 *
 * Positive navigation and Quick Documentation cases remain dedicated fixture work.
 *
 * Revision History
 * ----------------
 *
 * v1.5.1 — 2026-10-02 (JS)
 * • Moved configurable missing-resource policy matrices to pure ResourceClassifier unit tests.
 * • Kept this fixture focused on Python PSI and inspection integration behavior.
 *
 * v1.0.0 — 2026-08-25 (JS)
 * • Added the initial IntelliJ Platform/Python PSI inspection fixture.
 * • Added GitHub Issue #1 regression coverage for nested generic annotations.
 *
 * v1.1.0 — 2026-08-25 (JS)
 * • Replaced embedded Python source with the shared testData regression corpus.
 * • Expanded no-warning inspection coverage across GitHub Issues #1 through #8.
 * • Added an explicit testData path for reusable Python fixture files.
 *
 * v1.4.0 — 2026-10-02 (JS)
 * • Added GitHub Issue #10 coverage proving quoted paths in module docstrings are ignored.
 * • Added the bracketed missing-resource positive control for explicit docstring resources.
 *
 * v1.3.0 — 2026-10-02 (JS)
 * • Added GitHub Issue #9 regression coverage for Windows command-line switches.
 * • Added Issue #9 coverage for escaped fragments and example paths in module documentation.
 *
 * v1.2.0 — 2026-08-25 (JS)
 * • Retained the complete shared GitHub Issues #1–#8 Python regression-corpus test.
 * • Added a dedicated Kotlin assertion for Issue #8 positive missing-resource behavior.
 * • Documented why the positive Issue #8 diagnostic is evaluated here rather than in .py test data.
 */
class MissingResourceInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String =
        "src/test/testData"

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MissingResourceInspection())
    }

    /**
     * Verify that all fail-closed / ignored cases in the GitHub Issues #1–#8
     * regression corpus produce no Resource Navigator missing-resource warning.
     */
    fun testGitHubIssuesNoFalsePositiveDiagnostics() {

        myFixture.configureByFile(
            "git_hub_issues_1_to_8_082526.py",
        )

        myFixture.checkHighlighting()
    }


    /**
     * Issue #9: command-line arguments that begin with "/" are switches, not absolute
     * filesystem resources, and must not produce missing-resource diagnostics.
     */
    fun testIssue9CommandLineSwitchesProduceNoWarning() {

        myFixture.configureByText(
            "issue9_cli_switches.py",
            """
            import subprocess

            args = ["taskkill", "/F", "/IM", "chrome.exe"]
            subprocess.run(args)
            """.trimIndent(),
        )

        assertNoMissingWarnings()
    }

    /**
     * Issue #9: resource-looking paths and escaped fragments inside module documentation
     * are examples, not live resource-bearing values, and must not produce diagnostics.
     */
    fun testIssue9DocumentationExamplesProduceNoWarning() {

        val source = listOf(
            "r\"\"\"",
            "Example Output",
            "",
            "C:\\Users\\example\\project\\link.pdf",
            "C:\\Users\\example\\project\\link.mhtml",
            "\\\\n",
            "\"\"\"",
        ).joinToString("\n")

        myFixture.configureByText(
            "issue9_documentation_examples.py",
            source,
        )

        assertNoMissingWarnings()
    }

    /**
     * Issue #10: quoted filesystem paths inside a Python module docstring are report/example
     * text rather than explicit RN resources and must not produce missing-resource warnings.
     */
    fun testIssue10QuotedDocstringPathsProduceNoWarning() {

        val source = listOf(
            "r\"\"\"",
            "Example Output",
            "",
            "[ChatManagerStagingAggregate]",
            "",
            "\"C:\\Users\\example\\AppData\\Local\\Temp\\ChatManagerStagingAggregate\"",
            "\"C:\\Users\\example\\AppData\\Local\\Temp\\ChatManagerStagingAggregate\\chat_manager_path_tree.txt\"",
            "\"C:\\Users\\example\\AppData\\Local\\Temp\\ChatManagerStagingAggregate\\chat_manager_paths_inventory.txt\"",
            "\"C:\\Users\\example\\AppData\\Local\\Temp\\ChatManagerStagingAggregate\\chat_manager_paths_report.txt\"",
            "\"\"\"",
        ).joinToString("\n")

        myFixture.configureByText(
            "issue10_quoted_docstring_paths.py",
            source,
        )

        assertNoMissingWarnings()
    }

    /**
     * Issue #10 positive control: brackets explicitly opt a docstring path into RN resource
     * handling, so a bracketed missing path must still produce a missing-resource warning.
     */
    fun testIssue10BracketedMissingDocstringPathProducesWarning() {

        val missingPath = "C:\\RNFixture\\missing.json"
        val source = listOf(
            "r\"\"\"",
            "Example Output",
            "",
            "[$missingPath]",
            "\"\"\"",
        ).joinToString("\n")

        myFixture.configureByText(
            "issue10_bracketed_missing_docstring_path.py",
            source,
        )

        val warnings = myFixture.doHighlighting()
            .filter { it.description?.startsWith("Referenced resource does not exist:") == true }

        assertEquals(1, warnings.size)
        assertEquals(
            "Referenced resource does not exist: $missingPath",
            warnings.single().description,
        )
    }


    /** Issue #11: regex character classes must not be interpreted as bracketed resources. */
    fun testIssue11RegexCharacterClassesProduceNoWarning() {
        myFixture.configureByText(
            "issue11_regex_classes.py",
            """
            import re

            date = re.compile(r"\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b")
            letters = re.search(r"[a-z][0-9][._-][^/\\]", "abc")
            pieces = re.findall(
                r"[/-]"
                r"[a-z]"
                r"[0-9]",
                "test",
            )
            result = re.sub(r"[._-]", "_", "a-b")
            """.trimIndent(),
        )
        assertNoMissingWarnings()
    }

    /** Issue #11: non-regex bracketed citations must retain missing-path diagnostics. */
    fun testIssue11OrdinaryBracketedMissingPathStillWarns() {
        val missingPath = "C:\\RNFixture\\issue11_missing.json"
        myFixture.configureByText(
            "issue11_bracketed_missing.py",
            "resource = r\"[$missingPath]\"",
        )
        val warnings = myFixture.doHighlighting()
            .filter { it.description?.startsWith("Referenced resource does not exist:") == true }
        assertEquals(1, warnings.size)
        assertEquals("Referenced resource does not exist: $missingPath", warnings.single().description)
    }

    private fun assertNoMissingWarnings() {

        val warnings = myFixture.doHighlighting()
            .filter { it.description?.startsWith("Referenced resource does not exist:") == true }

        assertTrue(
            "Expected no Resource Navigator missing-resource warnings, got: " +
                    warnings.joinToString { it.description ?: "<no description>" },
            warnings.isEmpty(),
        )
    }

    /**
     * Issue #8 positive diagnostic.
     *
     * A statically resolvable Path composition must be recognized by RN and produce
     * exactly one missing-resource warning when the resolved target does not exist.
     */
    fun testIssue8StaticMissingResourceProducesWarning() {

        myFixture.configureByText(
            "issue8_static_missing.py",
            """
            from pathlib import Path

            TEST_OUTPUT_DIR = Path(r"C:\Temp")
            TEST_REGISTRY_FILE = TEST_OUTPUT_DIR / "_chapter_pages_registry.py"
            """.trimIndent(),
        )

        val warnings = myFixture.doHighlighting()
            .filter { it.description?.startsWith("Referenced resource does not exist:") == true }

        assertEquals(1, warnings.size)
        assertEquals(
            "Referenced resource does not exist: C:\\Temp\\_chapter_pages_registry.py",
            warnings.single().description,
        )
    }
}