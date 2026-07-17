package com.jschelert.resourcenavigator.util

import com.jschelert.resourcenavigator.config.ResourceNavigatorSettings
import com.jetbrains.python.psi.PyStringLiteralExpression
import java.net.URI

/**
 * =================================================================================================
 * ResourceClassifier
 * =================================================================================================
 *
 * Resource Classifier
 * -------------------
 * Determines whether a Python string literal should be interpreted as a
 * navigable resource by Resource Navigator.
 *
 * Behavior
 * --------
 * • Recognizes supported HTTP and HTTPS URLs.
 * • Recognizes supported local resource strings.
 * • Applies configurable filename extension filtering.
 * • Detects pathlib constructor usage.
 * • Applies lightweight filesystem path heuristics.
 * • Rejects multi-line and empty string literals.
 *
 * Responsibilities
 * ----------------
 * • Classify candidate resource strings.
 * • Detect supported URL schemes.
 * • Extract filename extensions.
 * • Apply configurable resource heuristics.
 *
 * Dependencies
 * ------------
 * • ResourceNavigatorSettings
 * • PythonResourceContext
 * • PyStringLiteralExpression
 * • URI
 *
 * See Also
 * --------
 * • PythonResourceContext
 * • PythonStringUtil
 * • ResourceResolver
 * • ResourceReferenceContributor
 *
 * Architectural Notes
 * -------------------
 * • ResourceClassifier intentionally determines only whether a string should
 *   be treated as a navigable resource.
 *
 * • Resource resolution, PSI navigation, and resource opening are delegated to
 *   their respective subsystems.
 *
 * • Classification uses inexpensive heuristics only and deliberately avoids
 *   filesystem access.
 */
object ResourceClassifier {

    /**
     * Supported URL schemes.
     */
    private val urlSchemes = setOf(
        "http",
        "https",
    )

    /**
     * Determine whether a Python string literal should be treated as a
     * navigable resource.
     *
     * Behavior
     * --------
     * • Rejects blank and multi-line strings.
     * • Recognizes supported URLs.
     * • Recognizes supported pathlib constructor arguments.
     * • Applies configurable filename extension matching.
     * • Applies optional broad filesystem-path matching.
     */
    fun shouldHandle(
        element: PyStringLiteralExpression,
        value: String,
    ): Boolean {

        if (
            value.isBlank() ||
            value.contains('\n') ||
            value.contains('\r')
        ) {
            return false
        }

        val settings =
            ResourceNavigatorSettings.getInstance()

        // --------------------------------------------------
        // URLs
        // --------------------------------------------------

        if (isUrl(value))
            return settings.state.enableUrlNavigation

        // --------------------------------------------------
        // Local resource heuristics
        // --------------------------------------------------

        val pathWrapped =
            PythonResourceContext.isPathWrapped(element)

        val recognizedExtension =
            extension(value) in settings.extensions()

        val looksLikePath =
            looksLikeFilePath(value)

        return pathWrapped ||
                (recognizedExtension && looksLikePath) ||
                (settings.state.acceptAnyExistingPath && looksLikePath)
    }

    /**
     * Return true if the supplied string is a supported HTTP or HTTPS URL.
     */
    fun isUrl(
        value: String,
    ): Boolean =
        runCatching {
            URI(value).scheme?.lowercase() in urlSchemes
        }.getOrDefault(false)

    /**
     * Return the lowercase filename extension.
     *
     * Examples
     * --------
     *     foo/bar/file.pdf        -> pdf
     *     C:\tmp\image.svg        -> svg
     *     report.mhtml?x=1        -> mhtml
     */
    fun extension(
        value: String,
    ): String {

        val filename =
            value
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .trim('"', '\'')

        return filename
            .substringAfterLast('.', "")
            .lowercase()
    }

    /**
     * Heuristically determine whether a string resembles a filesystem path.
     *
     * Behavior
     * --------
     * • Rejects unsupported URI schemes.
     * • Detects Unix-style paths.
     * • Detects Windows-style paths.
     * • Detects filename extensions.
     * • Detects Windows drive prefixes.
     */
    fun looksLikeFilePath(
        value: String,
    ): Boolean {

        if (
            value.startsWith("data:") ||
            value.startsWith("mailto:")
        ) {
            return false
        }

        return value.contains('/') ||
                value.contains('\\') ||
                value.contains('.') ||
                Regex("^[A-Za-z]:").containsMatchIn(value)
    }
}