package com.jschelert.resourcenavigator

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jschelert.resourcenavigator.inspection.MissingResourceInspection
import java.nio.file.Files
import java.nio.file.Path

/**
 * IntelliJ Platform fixture tests for Resource Navigator positive missing-resource diagnostics.
 *
 * These tests complement the shared GitHub Issues #1–#8 no-warning corpus by verifying
 * resource expressions that must be recognized by RN but whose resolved local targets
 * deliberately do not exist.
 *
 * Coverage
 * --------
 *
 * • Standalone missing PDF literal produces one RN warning.
 * • Quoted missing PDF in descriptive prose produces one RN warning on the quoted path only.
 * • Static pathlib.Path composition to a missing PDF produces one RN warning.
 * • Nested pathlib expressions emit one warning, not one per literal.
 * • Externally created PDF/MHTML exports do not trigger stale-VFS warnings.
 *
 * Test Strategy
 * -------------
 *
 * • Use unique real temporary directories so missing targets are deterministic.
 * • Never create the target resource itself.
 * • Assert exactly one MissingResourceInspection diagnostic per fixture.
 * • Assert the warning description contains the fully resolved missing path.
 *
 * Revision History
 * ----------------
 * v1.0.1 — 2026-11-23 (JS)
 * • Add nested-path deduplication and external-export existence regressions.
 *
 * v1.0.0 — 2026-08-25 (JS)
 * • Added dedicated positive missing-resource inspection coverage.
 * • Added standalone, quoted, and static pathlib.Path missing-resource cases.
 */
class MissingResourcePositiveDiagnosticsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MissingResourceInspection())
    }

    fun testStandaloneMissingPdfProducesWarning() {

        val directory = Files.createTempDirectory("rn-missing-standalone-")
        val missing = directory.resolve("missing.pdf")

        myFixture.configureByText(
            "missing_standalone.py",
            """
            resource = r"${pythonRawPath(missing)}"
            """.trimIndent(),
        )

        assertSingleMissingWarning(missing)
    }

    fun testQuotedMissingPdfProducesWarning() {

        val directory = Files.createTempDirectory("rn-missing-quoted-")
        val missing = directory.resolve("missing.pdf")

        myFixture.configureByText(
            "missing_quoted.py",
            """
            resource = r'Open "${pythonRawPath(missing)}" for details'
            """.trimIndent(),
        )

        assertSingleMissingWarning(missing)
    }

    fun testStaticPathMissingPdfProducesWarning() {

        val directory = Files.createTempDirectory("rn-missing-static-")
        val missing = directory.resolve("missing.pdf")

        myFixture.configureByText(
            "missing_static.py",
            """
            from pathlib import Path

            BASE = Path(r"${pythonRawPath(directory)}")
            resource = BASE / "missing.pdf"
            """.trimIndent(),
        )

        assertSingleMissingWarning(missing)
    }

    fun testNestedPathExpressionReportsOneMissingResource() {
        val directory = Files.createTempDirectory("rn-missing-nested-")
        val folder = directory.resolve("chat")
        Files.createDirectory(folder)
        val missing = folder.resolve("link.pdf")

        myFixture.configureByText(
            "missing_nested.py",
            """
            from pathlib import Path

            CHAT_EXPORT_PATH = Path(r"${pythonRawPath(directory)}")
            pdf_path = CHAT_EXPORT_PATH / "chat" / "link.pdf"
            """.trimIndent(),
        )

        assertSingleMissingWarning(missing)
    }

    fun testExternallyCreatedChatExportsDoNotWarnWithStaleVfs() {
        val directory = Files.createTempDirectory("rn-existing-chat-")
        val folder = directory.resolve("chat")
        Files.createDirectory(folder)
        Files.writeString(folder.resolve("link.pdf"), "PDF placeholder for inspection")
        Files.writeString(folder.resolve("link.mhtml"), "MHTML placeholder for inspection")

        myFixture.configureByText(
            "existing_chat_exports.py",
            """
            from pathlib import Path

            CHAT_EXPORT_PATH = Path(r"${pythonRawPath(directory)}")
            pdf_path = CHAT_EXPORT_PATH / "chat" / "link.pdf"
            mhtml_path = CHAT_EXPORT_PATH / "chat" / "link.mhtml"
            """.trimIndent(),
        )

        val warnings = myFixture.doHighlighting().filter {
            it.description?.startsWith("Referenced resource does not exist:") == true
        }
        assertTrue("Existing resources must not be reported missing: $warnings", warnings.isEmpty())
    }

    private fun assertSingleMissingWarning(expected: Path) {
        val warnings = myFixture.doHighlighting()
            .filter { it.description?.startsWith("Referenced resource does not exist:") == true }

        assertEquals(1, warnings.size)
        assertEquals(
            "Referenced resource does not exist: ${expected.toAbsolutePath().normalize()}",
            warnings.single().description,
        )
    }

    private fun pythonRawPath(path: Path): String =
        path.toAbsolutePath().normalize().toString()
}