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
 * v1.0.0 — 2026-08-25 (JS)
 * • Added the initial IntelliJ Platform/Python PSI inspection fixture.
 * • Added GitHub Issue #1 regression coverage for nested generic annotations.
 *
 * v1.1.0 — 2026-08-25 (JS)
 * • Replaced embedded Python source with the shared testData regression corpus.
 * • Expanded no-warning inspection coverage across GitHub Issues #1 through #8.
 * • Added an explicit testData path for reusable Python fixture files.
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