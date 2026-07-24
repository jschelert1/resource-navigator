package com.jschelert.resourcenavigator.preview

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.util.PythonStringResolver
import com.jschelert.resourcenavigator.util.ResourceKind
import com.jschelert.resourcenavigator.util.ResourceResolver
import java.nio.file.Files
import java.nio.file.Paths

/**
 * =================================================================================================
 * ResourceDocumentationProvider
 * =================================================================================================
 *
 * Quick Documentation Provider
 * ----------------------------
 * Provides IntelliJ Quick Documentation (Ctrl+Q) for Resource Navigator
 * resources referenced from Python string literals.
 *
 * Behavior
 * --------
 * • Locates the Python string literal associated with the documentation request.
 * • Resolves Python strings through PythonStringResolver.
 * • Resolves reconstructed string values through ResourceResolver.
 * • Displays URL information for HTTP/HTTPS resources.
 * • Displays existence status, detected file type, file size, and resolved path
 *   for local filesystem resources.
 * • Escapes dynamic HTML content before rendering documentation.
 *
 * Responsibilities
 * ----------------
 * • Locate the enclosing Python string literal.
 * • Evaluate the Python literal into its reconstructed compile-time value.
 * • Resolve the reconstructed value into a ResourceTarget.
 * • Generate concise Quick Documentation HTML for the resolved resource.
 * • Present local-resource metadata without performing navigation.
 *
 * Dependencies
 * ------------
 * • PythonStringResolver
 * • ResourceResolver
 * • ResourceKind
 * • PyStringLiteralExpression
 * • AbstractDocumentationProvider
 * • StringUtil
 * • java.nio.file.Files
 * • java.nio.file.Paths
 *
 * Resolution Flow
 * ---------------
 *
 *     PsiElement
 *         |
 *         v
 *     PyStringLiteralExpression
 *         |
 *         v
 *     PythonStringResolver
 *         |
 *         v
 *     PythonResolvedString
 *         |
 *         v
 *     ResourceResolver
 *         |
 *         v
 *     ResourceTarget
 *         |
 *         +----> URL metadata
 *         |
 *         +----> Local filesystem metadata
 *         |
 *         v
 *     Quick Documentation HTML
 *
 * PythonStringResolver determines the reconstructed compile-time value of the
 * Python literal. ResourceResolver then interprets that value as a supported
 * Resource Navigator resource. The resulting ResourceTarget supplies the
 * metadata used to construct the Quick Documentation display.
 *
 * Architectural Notes
 * -------------------
 * • Documentation generation is intentionally read-only and never performs
 *   navigation.
 *
 * • Python string evaluation and resource resolution are intentionally
 *   separated. PythonStringResolver determines what the Python expression
 *   means, while ResourceResolver determines what resource that value
 *   represents.
 *
 * • Resource resolution uses the same PythonStringResolver → ResourceResolver
 *   pipeline used by the navigation subsystem, keeping documentation behavior
 *   consistent with resource navigation.
 *
 * • Local file size is queried only when the resolved resource exists and can
 *   be represented as a java.nio.file.Path.
 *
 * • HTML escaping is centralized within this class to ensure arbitrary file
 *   names, paths, file types, and URLs are rendered safely inside IntelliJ's
 *   documentation pane.
 *
 * See Also
 * --------
 * • PythonStringResolver
 * • ResourceResolver
 * • ResourceReferenceContributor
 * • ResourceGotoDeclarationHandler
 * • ResourceDispatcher
 * • ResourceReferenceLocal
 * • ResourceReferenceUrl
 *
 * Revision History
 * ----------------
 * v2.0.0 — 2026-07-23 (JS)
 * • Updated Quick Documentation resource handling to evaluate Python string
 *   literals through PythonStringResolver before resource resolution.
 * • Separated Python string evaluation from ResourceResolver resource
 *   resolution.
 * • Updated documentation generation to operate on the reconstructed
 *   compile-time string value.
 * • Documented the shared PythonStringResolver → ResourceResolver resolution
 *   pipeline used by Quick Documentation and resource navigation.
 */
class ResourceDocumentationProvider : AbstractDocumentationProvider() {

    /**
     * Generate Quick Documentation for the resource beneath the caret.
     *
     * Behavior
     * --------
     * • Displays URL information for HTTP/HTTPS resources.
     * • Displays status, type, size, and path for local resources.
     * • Returns null when the current element is not a supported resource.
     *
     * Documentation Behavior
     * ----------------------
     *
     * +--------------------------------------+-----------------------+----------------------------------+
     * | Python String Example                | Resource State        | Quick Documentation             |
     * +--------------------------------------+-----------------------+----------------------------------+
     * | 'docs/manual.pdf'                    | Existing local file   | Status, type, size, and path     |
     * | 'docs/missing.pdf'                   | Missing local file    | Missing status, type, and path   |
     * | 'C:\Documents\report.pdf'            | Existing local file   | Status, type, size, and path     |
     * | 'https://example.com'                | HTTP/HTTPS URL        | URL and browser-open information |
     * | '*.pdf'                              | Unsupported/glob      | None                             |
     * | 'ordinary text'                      | Not a resource        | None                             |
     * | f'{runtime_value}'                   | Unresolved expression | None                             |
     * +--------------------------------------+-----------------------+----------------------------------+
     *
     * Local file size is displayed only when the resource exists and its
     * resolved path can be queried successfully.
     */
    override fun generateDoc(
        element: PsiElement,
        originalElement: PsiElement?,
    ): String? {

        //
        // Locate the enclosing Python string literal PSI element.
        //
        val literal =
            findLiteral(
                originalElement ?: element,
            ) ?: return null

        //
        // Resolve the Python literal into its reconstructed compile-time value.
        //
        val resolved =
            PythonStringResolver.resolve(literal)
                ?: return null

        //
        // Resolve the reconstructed string value as a resource.
        //
        val target =
            ResourceResolver.resolve(
                literal,
                resolved,
            ) ?: return null

        //
        // Escape the original resource value for safe HTML rendering.
        //
        val escapedRaw =
            html(target.sourceValue)

        //
        // Generate Quick Documentation for URL resources.
        //
        if (target.kind == ResourceKind.URL) {
            return """
                <b>Resource URL</b><br>
                <code>$escapedRaw</code><br><br>
                Ctrl-click to open in the browser.
            """.trimIndent()
        }

        //
        // Determine the resolved local filesystem path.
        //
        val path =
            target.resolvedPath
                ?: target.sourceValue

        //
        // Convert the resolved path into a filesystem Path when possible.
        //
        val file =
            runCatching {
                Paths.get(path)
            }.getOrNull()

        //
        // Determine whether the local resource exists.
        //
        val exists =
            target.exists

        //
        // Determine the local resource size when available.
        //
        val size =
            if (exists && file != null) {
                runCatching {
                    Files.size(file)
                }.getOrNull()
            } else {
                null
            }

        //
        // Determine the IntelliJ file-type description.
        //
        val type =
            target.virtualFile
                ?.fileType
                ?.description
                ?: "File"

        //
        // Construct the human-readable resource status.
        //
        val status =
            if (exists) {
                "Exists"
            } else {
                "Missing"
            }

        //
        // Construct the optional formatted file-size metadata.
        //
        val sizeText =
            size?.let {
                "<br><b>Size:</b> ${StringUtil.formatFileSize(it)}"
            } ?: ""

        //
        // Generate Quick Documentation for the local resource.
        //
        return """
            <b>Resource</b><br>
            <b>Status:</b> $status<br>
            <b>Type:</b> ${html(type)}$sizeText<br>
            <b>Path:</b> <code>${html(path)}</code><br><br>
            Ctrl-click to open the resource.
        """.trimIndent()
    }

    /**
     * Locate the enclosing Python string literal for the supplied PSI element.
     */
    private fun findLiteral(
        element: PsiElement,
    ): PyStringLiteralExpression? =

        element as? PyStringLiteralExpression
            ?: PsiTreeUtil.getParentOfType(
                element,
                PyStringLiteralExpression::class.java,
                false,
            )

    /**
     * Escape HTML special characters for safe rendering within IntelliJ Quick
     * Documentation.
     */
    private fun html(
        value: String,
    ): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}