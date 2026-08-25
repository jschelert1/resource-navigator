package com.jschelert.resourcenavigator

import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.vfs.LocalFileSystem
import com.jschelert.resourcenavigator.navigation.ResourceNavigationElement
import java.nio.file.Files
import java.nio.file.Path

/**
 * IntelliJ Platform fixture tests for Resource Navigator reference/navigation behavior.
 *
 * These tests complement MissingResourceInspectionTest by verifying that RN creates
 * navigable PSI references for supported resource expressions and fails closed for
 * unsupported expressions.
 *
 * Test Strategy
 * -------------
 *
 * • Create both Python sources and resource targets on the real temporary filesystem.
 * • Open Python sources through LocalFileSystem so ResourceResolver can map their directories.
 * • Place <caret> inside the Python resource text that should own the RN reference.
 * • Locate the owning Python string literal and inspect its contributed PSI references.
 * • Resolve the RN reference and compare its target path with the real temporary target.
 * • Assert that unsupported/fail-closed expressions do not expose an RN reference.
 *
 * Initial Coverage
 * ----------------
 *
 * • Standalone resource literal resolves to an existing file.
 * • pathlib.Path composition resolves to an existing file.
 * • Issue #5 previous-keyword pseudo-scope resolves the file_path resource.
 * • Issue #5 forward-keyword pseudo-scope remains unresolved by RN.
 * • Issue #5 pseudo-scope does not escape the call.
 * • Quoted PDF resource resolves only the quoted resource text.
 * • Quoted DOCX resource resolves only the quoted resource text.
 * • Quoted JPG resource resolves only the quoted resource text.
 *
 * Revision History
 * ----------------
 *
 * v1.4.0 — 2026-08-25 (JS)
 * • Added automated quoted-resource navigation tests for PDF, DOCX, and JPG.
 * • Verify each quoted resource resolves through the production synthetic navigation target.
 * • Keep quoted-resource fixtures on the real filesystem to match production resolution semantics.
 *
 * v1.3.1 — 2026-08-25 (JS)
 * • Updated positive navigation assertions for RN's synthetic ResourceNavigationElement.
 * • Assert target location through getLocationString() instead of resolved.containingFile.
 * • Preserve production navigation policy while verifying the resolved resource path.
 *
 * v1.3.0 — 2026-08-25 (JS)
 * • Replaced configureByText() virtual Python sources with real temporary .py files.
 * • Open real source files through LocalFileSystem before configuring the fixture.
 * • Keep source and target filesystem-backed to match production resolution semantics.
 *
 * v1.2.2 — 2026-08-25 (JS)
 * • Replaced VirtualFile.toNioPath() in test assertions because some IntelliJ
 *   VirtualFile implementations used by the test platform do not support it.
 * • Compare navigation targets through VirtualFile.path normalized as java.nio.file.Path.
 *
 * v1.2.1 — 2026-08-25 (JS)
 * • Removed two always-constant helper parameters flagged by IntelliJ inspection.
 * • Confirmed old v1.1.0 "targets/..." failure diagnostics are absent from this source.
 *
 * v1.2.0 — 2026-08-25 (JS)
 * • Replaced in-memory fixture targets with real OS temporary files/directories.
 * • Updated positive navigation tests to use absolute filesystem paths.
 * • Preserved Issue #5 forward-reference and outside-call fail-closed boundaries.
 *
 * v1.1.0 — 2026-08-25 (JS)
 * • Made RN reference discovery PSI-element-aware instead of relying on PsiFile.findReferenceAt().
 * • Added diagnostics that distinguish missing reference contribution from failed target resolution.
 *
 * v1.0.0 — 2026-08-25 (JS)
 * • Added the initial automated RN reference/navigation fixture tests.
 */
class ResourceReferenceNavigationTest : BasePlatformTestCase() {

    /**
     * A plain resource literal must resolve to the existing fixture target.
     */
    fun testStandaloneResourceLiteralResolves() {

        val target = createStandaloneTempTarget()
        val resource = insertCaretInFileName(pythonRawPath(target), "standalone.txt")

        configureRealPythonFile(
            target.parent,
            "standalone.py",
            """
            resource = r"$resource"
            """.trimIndent(),
        )

        assertCaretResolvesTo(target)
    }

    /**
     * A statically resolvable pathlib.Path composition must resolve to its target.
     */
    fun testStaticPathCompositionResolves() {

        val directory = Files.createTempDirectory("rn-navigation-static-")
        val target = createTarget(directory, "static.xlsx")
        val basePath = pythonRawPath(directory)

        configureRealPythonFile(
            directory,
            "static_path.py",
            """
            from pathlib import Path

            BASE = Path(r"$basePath")
            resource = BASE / "stat<caret>ic.xlsx"
            """.trimIndent(),
        )

        assertCaretResolvesTo(target)
    }

    /**
     * Issue #5: a later keyword argument may use a statically resolvable value
     * declared by an earlier keyword argument in the same call.
     */
    fun testIssue5PreviousKeywordReferenceResolves() {

        val directory = Files.createTempDirectory("rn-navigation-issue5-")
        val target = createTarget(directory, "issue5.xlsx")
        val basePath = pythonRawPath(directory)

        configureRealPythonFile(
            directory,
            "issue5_previous_keyword.py",
            """
            from pathlib import Path

            class SomeClass:
                def __init__(self, base_path: Path, file_path: Path) -> None:
                    self.base_path = base_path
                    self.file_path = file_path

            resource = SomeClass(
                base_path=Path(r"$basePath"),
                file_path=base_path / "issue<caret>5.xlsx",
            )
            """.trimIndent(),
        )

        assertCaretResolvesTo(target)
    }

    /**
     * A quoted PDF embedded in descriptive prose must resolve only the quoted
     * resource text to the existing PDF target.
     */
    fun testQuotedPdfResolves() {

        val directory = Files.createTempDirectory("rn-navigation-quoted-pdf-")
        val target = createTarget(directory, "quoted.pdf")
        val targetPath = pythonRawPath(target)
        val resource = insertCaretInFileName(targetPath, "quoted.pdf")

        configureRealPythonFile(
            directory,
            "quoted_pdf.py",
            """
            resource = r'Read "$resource" for background'
            """.trimIndent(),
        )

        assertCaretResolvesTo(target)
    }

    /**
     * A quoted DOCX embedded in descriptive prose must resolve only the quoted
     * resource text to the existing DOCX target.
     */
    fun testQuotedDocxResolves() {

        val directory = Files.createTempDirectory("rn-navigation-quoted-docx-")
        val target = createTarget(directory, "quoted.docx")
        val targetPath = pythonRawPath(target)
        val resource = insertCaretInFileName(targetPath, "quoted.docx")

        configureRealPythonFile(
            directory,
            "quoted_docx.py",
            """
            resource = r'Figure source: "$resource"'
            """.trimIndent(),
        )

        assertCaretResolvesTo(target)
    }

    /**
     * A quoted JPG embedded in descriptive prose must resolve only the quoted
     * resource text to the existing JPG target.
     */
    fun testQuotedJpgResolves() {

        val directory = Files.createTempDirectory("rn-navigation-quoted-jpg-")
        val target = createTarget(directory, "quoted.jpg")
        val targetPath = pythonRawPath(target)
        val resource = insertCaretInFileName(targetPath, "quoted.jpg")

        configureRealPythonFile(
            directory,
            "quoted_jpg.py",
            """
            resource = r'View image "$resource"'
            """.trimIndent(),
        )

        assertCaretResolvesTo(target)
    }

    /**
     * Issue #5 boundary: a keyword value must not resolve through a keyword
     * argument that appears later in the same call.
     */
    fun testIssue5ForwardKeywordReferenceIsIgnored() {

        val directory = Files.createTempDirectory("rn-navigation-forward-")
        createTarget(directory, "issue5_forward.xlsx")
        val basePath = pythonRawPath(directory)

        configureRealPythonFile(
            directory,
            "issue5_forward_keyword.py",
            """
            from pathlib import Path

            class SomeClass:
                def __init__(self, base_path: Path, file_path: Path) -> None:
                    self.base_path = base_path
                    self.file_path = file_path

            resource = SomeClass(
                file_path=base_path / "issue5_for<caret>ward.xlsx",
                base_path=Path(r"$basePath"),
            )
            """.trimIndent(),
        )

        assertNull(findResourceReferenceAtCaret())
    }

    /**
     * Issue #5 boundary: the keyword pseudo-scope is local to its call and must
     * not make the keyword name resolvable in a later expression.
     */
    fun testIssue5KeywordPseudoScopeDoesNotEscapeCall() {

        val directory = Files.createTempDirectory("rn-navigation-outside-")
        createTarget(directory, "issue5.xlsx")
        createTarget(directory, "issue5_outside.xlsx")
        val basePath = pythonRawPath(directory)

        configureRealPythonFile(
            directory,
            "issue5_outside_call.py",
            """
            from pathlib import Path

            class SomeClass:
                def __init__(self, base_path: Path, file_path: Path) -> None:
                    self.base_path = base_path
                    self.file_path = file_path

            resource = SomeClass(
                base_path=Path(r"$basePath"),
                file_path=base_path / "issue5.xlsx",
            )

            outside = base_path / "issue5_out<caret>side.xlsx"
            """.trimIndent(),
        )

        assertNull(findResourceReferenceAtCaret())
    }

    /**
     * Write a Python fixture to the real OS filesystem and configure the IntelliJ
     * fixture from its LocalFileSystem-backed VirtualFile.
     */
    private fun configureRealPythonFile(directory: Path, fileName: String, sourceWithCaret: String) {
        val caretMarker = "<caret>"
        val caretOffset = sourceWithCaret.indexOf(caretMarker)

        require(caretOffset >= 0) {
            "Python navigation fixture must contain a <caret> marker."
        }

        val source = sourceWithCaret.replace(caretMarker, "")
        val sourcePath = directory.resolve(fileName)

        Files.writeString(sourcePath, source)
        sourcePath.toFile().deleteOnExit()

        val virtualFile = LocalFileSystem.getInstance()
            .refreshAndFindFileByNioFile(sourcePath.toAbsolutePath().normalize())

        assertNotNull(
            "Expected LocalFileSystem to find real Python fixture: $sourcePath",
            virtualFile,
        )

        myFixture.configureFromExistingVirtualFile(virtualFile!!)
        myFixture.editor.caretModel.moveToOffset(caretOffset)
    }

    private fun createStandaloneTempTarget(): Path {
        val directory = Files.createTempDirectory("rn-navigation-")
        return createTarget(directory, "standalone.txt")
    }

    private fun createTarget(directory: Path, fileName: String): Path {
        val target = directory.resolve(fileName)
        Files.writeString(target, "Resource Navigator navigation fixture.\n")
        target.toFile().deleteOnExit()
        directory.toFile().deleteOnExit()
        return target
    }

    private fun pythonRawPath(path: Path): String =
        path.toAbsolutePath().normalize().toString()

    private fun insertCaretInFileName(value: String, fileName: String): String {
        val markerOffset = fileName.length / 2
        val absoluteOffset = value.length - fileName.length + markerOffset
        return value.substring(0, absoluteOffset) + "<caret>" + value.substring(absoluteOffset)
    }

    /**
     * Find the Resource Navigator reference contributed to the Python string literal
     * containing the fixture caret.
     *
     * PsiReference ranges are relative to their owning PSI element, so querying the
     * PsiFile directly at the absolute caret offset can miss a valid contributed
     * reference. Inspect the owning string literal and test each reference range
     * against the caret's element-relative offset instead.
     */
    private fun findResourceReferenceAtCaret(): PsiReference? {
        val caretOffset = myFixture.caretOffset
        val leaf = myFixture.file.findElementAt(caretOffset)
            ?: myFixture.file.findElementAt((caretOffset - 1).coerceAtLeast(0))
            ?: return null

        val literal = PsiTreeUtil.getParentOfType(leaf, PyStringLiteralExpression::class.java, false)
            ?: return null

        val relativeOffset = caretOffset - literal.textRange.startOffset

        return literal.references.firstOrNull { reference ->
            reference.rangeInElement.containsOffset(relativeOffset)
        }
    }

    private fun assertCaretResolvesTo(expected: Path) {
        val reference = findResourceReferenceAtCaret()

        assertNotNull(
            referenceDiagnostic("Expected Resource Navigator reference at caret."),
            reference,
        )

        val resolved = reference!!.resolve()

        assertNotNull(
            referenceDiagnostic("Expected Resource Navigator reference to resolve.", reference),
            resolved,
        )
        assertTrue(
            "Expected Resource Navigator synthetic navigation element, got ${resolved!!::class.qualifiedName}",
            resolved is ResourceNavigationElement,
        )

        val location = (resolved as ResourceNavigationElement).locationString
        val resolvedPath = Path.of(location).toAbsolutePath().normalize()

        assertEquals(expected.toAbsolutePath().normalize(), resolvedPath)
    }

    /**
     * Include the owning PSI element and contributed reference ranges in assertion
     * failures so fixture/reference-provider problems are immediately distinguishable
     * from resolver failures.
     */
    private fun referenceDiagnostic(message: String, reference: PsiReference? = null): String {
        val caretOffset = myFixture.caretOffset
        val leaf = myFixture.file.findElementAt(caretOffset)
            ?: myFixture.file.findElementAt((caretOffset - 1).coerceAtLeast(0))
        val literal = leaf?.let {
            PsiTreeUtil.getParentOfType(it, PyStringLiteralExpression::class.java, false)
        }

        val references = literal?.references?.joinToString(
            prefix = "[",
            postfix = "]",
        ) { candidate ->
            "${candidate.javaClass.simpleName}:${candidate.rangeInElement}"
        } ?: "[]"

        return buildString {
            append(message)
            append(" caret=").append(caretOffset)
            append(", leaf=").append(leaf?.javaClass?.simpleName ?: "<none>")
            append(", literal=").append(literal?.text ?: "<none>")
            append(", references=").append(references)
            reference?.let {
                append(", selected=").append(it.javaClass.simpleName)
                append(':').append(it.rangeInElement)
                val resolvedTarget = it.resolve()
                if (resolvedTarget is ResourceNavigationElement) {
                    append(", targetLabel=").append(resolvedTarget.presentableText)
                    append(", targetLocation=").append(resolvedTarget.locationString)
                }
            }
        }
    }
}