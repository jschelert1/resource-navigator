package com.jschelert.resourcenavigator.preview

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyStringLiteralExpression
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
 * • Displays metadata for local resources.
 * • Displays URL information for HTTP/HTTPS resources.
 * • Indicates whether local resources exist.
 * • Displays detected file type and file size.
 * • Escapes HTML content before rendering documentation.
 *
 * Responsibilities
 * ----------------
 * • Generate Quick Documentation HTML.
 * • Locate the enclosing Python string literal.
 * • Resolve resources using ResourceResolver.
 * • Present concise resource metadata to the user.
 *
 * Dependencies
 * ------------
 * • ResourceResolver
 * • ResourceKind
 * • PyStringLiteralExpression
 * • AbstractDocumentationProvider
 * • StringUtil
 *
 * See Also
 * --------
 * • ResourceReferenceContributor
 * • ResourceGotoDeclarationHandler
 * • ResourceDispatcher
 * • ResourceReferenceLocal
 * • ResourceReferenceUrl
 *
 * Architectural Notes
 * -------------------
 * • Documentation generation is intentionally read-only and never performs
 *   navigation.
 *
 * • Resource resolution is delegated entirely to ResourceResolver so that the
 *   documentation provider shares the same resolution rules as every other
 *   navigation subsystem.
 *
 * • HTML escaping is centralized within this class to ensure arbitrary file
 *   names and URLs are displayed safely inside IntelliJ's documentation pane.
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
     */
    override fun generateDoc(
        element: PsiElement,
        originalElement: PsiElement?,
    ): String? {

        val literal =
            findLiteral(
                originalElement ?: element,
            ) ?: return null

        val target =
            ResourceResolver.resolve(literal)
                ?: return null

        val escapedRaw =
            html(target.rawValue)

        if (target.kind == ResourceKind.URL) {
            return """
                <b>Resource URL</b><br>
                <code>$escapedRaw</code><br><br>
                Ctrl-click to open in the browser.
            """.trimIndent()
        }

        val path =
            target.resolvedPath
                ?: target.rawValue

        val file =
            runCatching {
                Paths.get(path)
            }.getOrNull()

        val exists =
            target.exists

        val size =
            if (exists && file != null) {
                runCatching {
                    Files.size(file)
                }.getOrNull()
            } else {
                null
            }

        val type =
            target.virtualFile
                ?.fileType
                ?.description
                ?: "File"

        val status =
            if (exists) {
                "Exists"
            } else {
                "Missing"
            }

        val sizeText =
            size?.let {
                "<br><b>Size:</b> ${StringUtil.formatFileSize(it)}"
            } ?: ""

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