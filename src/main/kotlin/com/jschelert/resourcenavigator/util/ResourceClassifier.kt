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
 * Determines whether a resolved Python string value should be interpreted as a
 * navigable resource by Resource Navigator.
 *
 * Classification operates on the semantic value produced by Python string
 * evaluation while retaining the originating PyStringLiteralExpression for
 * syntax-context checks such as pathlib constructor detection.
 *
 * Behavior
 * --------
 * • Recognizes supported HTTP and HTTPS URLs.
 * • Recognizes supported local resource paths.
 * • Applies configurable filename-extension filtering.
 * • Recognizes strings used as pathlib constructor arguments.
 * • Applies lightweight filesystem-path heuristics.
 * • Rejects blank values and resolved values containing line breaks.
 * • Rejects filesystem glob patterns.
 * • Provides additional filtering for missing-resource inspection.
 * • Recognizes absolute filesystem paths regardless of filename extension.
 *
 * Responsibilities
 * ----------------
 * • Classify resolved Python string values as candidate resources.
 * • Detect supported URL schemes.
 * • Detect pathlib constructor context.
 * • Extract normalized filename extensions.
 * • Apply configurable local-resource heuristics.
 * • Exclude glob patterns representing collections rather than resources.
 * • Determine whether a candidate is appropriate for missing-resource
 *   inspection.
 *
 * Classification Flow
 * -------------------
 *
 *     PyStringLiteralExpression + resolved value
 *                      |
 *                      v
 *               shouldHandle()
 *                      |
 *          +-----------+-----------+
 *          |                       |
 *          v                       v
 *        URL?                Local resource?
 *          |                       |
 *          v                       +----> pathlib constructor
 *     URL setting                  |
 *                                  +----> absolute filesystem path
 *                                  |
 *                                  +----> recognized extension
 *                                  |          +
 *                                  |      path-like value
 *                                  |
 *                                  +----> broad path matching
 *                                             |
 *                                             v
 *                                    navigable candidate
 *
 * Missing-Resource Inspection
 * ---------------------------
 *
 *     PythonResolvedString
 *              |
 *              v
 *     shouldInspectMissing()
 *              |
 *              +----> reject empty value
 *              +----> reject unresolved braces
 *              +----> reject leading-dot value
 *              +----> reject glob pattern
 *              |
 *              v
 *         shouldHandle()
 *
 * Examples
 * --------
 *
 * +--------------------------------------+--------------------------------------+-------------------------+
 * | Python / Resolved Value              | Classification                       | Candidate               |
 * +--------------------------------------+--------------------------------------+-------------------------+
 * | 'docs/manual.pdf'                    | Recognized extension + path-like      | Local resource          |
 * | r'C:\Docs\manual.pdf'                | Recognized extension + path-like      | Local resource          |
 * | Path('manual.pdf')                   | pathlib constructor argument         | Local resource          |
 * | 'https://example.com'                | Supported URL scheme                 | URL                     |
 * | '*.pdf'                              | Glob pattern                         | Rejected                |
 * | 'docs/*.pdf'                         | Glob pattern                         | Rejected                |
 * | ''                                   | Blank value                          | Rejected                |
 * | 'line1\nline2'                       | Resolved value contains line break   | Rejected                |
 * +--------------------------------------+--------------------------------------+-------------------------+
 *
 * A multiline or adjacent Python source expression is not rejected merely
 * because it occupies multiple source lines. Classification operates on the
 * resolved semantic value supplied to shouldHandle().
 *
 * Dependencies
 * ------------
 * • ResourceNavigatorSettings
 * • PythonResourceContext
 * • PythonResolvedString
 * • PyStringLiteralExpression
 * • URI
 *
 * Architectural Notes
 * -------------------
 * • ResourceClassifier determines whether a resolved value should be considered
 *   a resource candidate; it does not resolve that resource against the
 *   filesystem.
 *
 * • Python expression evaluation is owned by PythonStringResolver.
 *   ResourceClassifier consumes the resulting semantic value rather than
 *   evaluating Python expressions itself.
 *
 * • The originating PyStringLiteralExpression is retained because some
 *   classification decisions depend on Python syntax context, particularly
 *   pathlib constructor usage.
 *
 * • Classification uses inexpensive string and PSI heuristics and deliberately
 *   avoids filesystem access.
 *
 * • Resource existence, path resolution, PSI navigation, and resource opening
 *   remain delegated to their respective subsystems.
 *
 * • Glob patterns are intentionally rejected because they describe sets of
 *   resources rather than one concrete navigation target.
 *
 * See Also
 * --------
 * • PythonStringResolver
 * • PythonResolvedString
 * • PythonResourceContext
 * • PythonStringUtil
 * • ResourceResolver
 * • ResourceReferenceContributor
 *
 * Revision History
 * ----------------
 * v2.0.0 — 2026-07-23 (JS)
 * • Updated classification to operate on resolved compile-time Python string
 *   values produced by PythonStringResolver.
 * • Added PythonResolvedString-based missing-resource inspection filtering.
 * • Added explicit glob-pattern rejection for navigation and inspection.
 * • Documented pathlib-context classification and the separation between
 *   Python evaluation, resource classification, and filesystem resolution.
 * • Clarified that multiline source expressions remain supported when their
 *   resolved semantic value represents a valid resource.
 * • Added classification of absolute filesystem paths regardless of filename
 *   extension, enabling directory and extensionless-resource candidates.
 * • Clarified that multiline source expressions remain supported when their
 *   resolved semantic value represents a valid resource.
 * • Added classification of absolute Windows, UNC, and Unix filesystem paths
 *   regardless of filename extension, enabling direct navigation to directories
 *   and extensionless resources.
 */
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
 * Determine whether a resolved Python string value should be treated as a
 * navigable resource candidate.
 *
 * Resource Classification
 * -----------------------
 *
 * +--------------------------------------+--------------------------------------+----------------------+
 * | Resolved Value / Context             | Classification                       | Result               |
 * +--------------------------------------+--------------------------------------+----------------------+
 * | "https://example.com"                | Supported URL                        | URL setting          |
 * | "docs/manual.pdf"                    | Extension + path-like value          | true                 |
 * | "manual.pdf"                         | Extension + path-like value          | true                 |
 * | Path("manual")                       | pathlib constructor argument         | true                 |
 * | "docs/manual.xyz"                    | Unrecognized extension               | Setting dependent    |
 * | "docs/manual"                        | Path-like, no recognized extension   | Setting dependent    |
 * | "*.pdf"                              | Glob pattern                         | false                |
 * | "docs/*.pdf"                         | Glob pattern                         | false                |
 * | ""                                   | Blank value                          | false                |
 * | "line1\nline2"                       | Resolved value contains line break   | false                |
 * | "C:\Docs"                            | Absolute filesystem path             | true                 |
 * | "/home/user/docs"                    | Absolute filesystem path             | true                 |
 * +--------------------------------------+--------------------------------------+----------------------+
 *
 * Behavior
 * --------
 * • Rejects blank resolved values.
 * • Rejects resolved values containing line breaks.
 * • Rejects filesystem glob patterns.
 * • Recognizes supported HTTP and HTTPS URLs when URL navigation is enabled.
 * • Recognizes arguments to supported pathlib constructors.
 * • Recognizes path-like values with configured filename extensions.
 * • Optionally accepts broader path-like values when acceptAnyPathLikeValue
 *   is enabled.
 *
 * This function performs classification only. It does not access the
 * filesystem or determine whether a local resource actually exists.
 *
 * Multiline or adjacent Python source literals remain supported when their
 * reconstructed compile-time value contains no actual line breaks.
 */
 */

    fun shouldHandle(
        element: PyStringLiteralExpression,
        value: String,
    ): Boolean {

        //
        // Reject blank values and semantic values containing line breaks.
        //
        if (
            value.isBlank() ||
            value.contains('\n') ||
            value.contains('\r')
        ) {
            return false
        }

        //
        // Reject glob patterns representing collections of resources.
        //
        if (isGlobPattern(value)) {
            return false
        }

        //
        // Load the current Resource Navigator classification settings.
        //
        val settings =
            ResourceNavigatorSettings.getInstance()

        /*
         * --------------------------------------------------
         * URL classification
         * --------------------------------------------------
        */

        //
        // Accept supported URLs only when URL navigation is enabled.
        //
        if (isUrl(value))
            return settings.state.enableUrlNavigation

        /*
         * --------------------------------------------------
         * Local resource classification
         * --------------------------------------------------
        */

        //
        // Determine whether the literal occurs inside a pathlib constructor.
        //
        val pathWrapped =
            PythonResourceContext.isPathWrapped(element)

        //
        // Determine whether the filename extension is explicitly recognized.
        //
        val recognizedExtension =
            extension(value) in settings.extensions()

        //
        // Determine whether the value syntactically resembles a filesystem path.
        //
        val looksLikePath =
            looksLikeFilePath(value)

        //
        // Determine whether the value represents an explicit absolute filesystem path.
        //
        val absolutePath =
            looksLikeAbsolutePath(value)

        //
        // Accept explicit pathlib arguments, absolute filesystem paths,
        // recognized path-like resources, or broadly accepted path-like
        // values when configured.
        //
        return pathWrapped ||
                absolutePath ||
                (recognizedExtension && looksLikePath) ||
                (settings.state.acceptAnyPathLikeValue && looksLikePath)
    }

    /**
     * Determine whether a resolved Python string should participate in the
     * missing-resource inspection.
     *
     * Missing-Resource Inspection
     * ---------------------------
     *
     * +--------------------------------------+--------------------------------------+------------------+
     * | Resolved Value                       | Inspection Decision                  | Result           |
     * +--------------------------------------+--------------------------------------+------------------+
     * | "docs/manual.pdf"                    | Valid resource candidate             | shouldHandle()   |
     * | "C:\Docs\manual.pdf"                 | Valid resource candidate             | shouldHandle()   |
     * | ""                                   | Empty value                          | false            |
     * | "{filename}"                         | Unresolved interpolation braces      | false            |
     * | "docs/{filename}.pdf"                | Unresolved interpolation braces      | false            |
     * | ".gitignore"                         | Leading-dot value                    | false            |
     * | "*.pdf"                              | Glob pattern                         | false            |
     * | "docs/*.pdf"                         | Glob pattern                         | false            |
     * | "ordinary text"                      | Not a resource candidate             | false            |
     * +--------------------------------------+--------------------------------------+------------------+
     *
     * Behavior
     * --------
     * • Uses the reconstructed compile-time value from PythonResolvedString.
     * • Trims surrounding whitespace before inspection.
     * • Rejects empty values.
     * • Rejects values containing unresolved brace syntax.
     * • Rejects leading-dot values.
     * • Rejects filesystem glob patterns.
     * • Delegates final resource classification to shouldHandle().
     *
     * This function determines only whether a literal is eligible for missing-
     * resource inspection. ResourceResolver subsequently determines whether the
     * candidate actually resolves to an existing resource.
     *
     * The additional filters prevent intentionally dynamic, special, or
     * collection-oriented strings from generating misleading missing-resource
     * warnings.
     */
     */
    fun shouldInspectMissing(
        literal: PyStringLiteralExpression,
        resolved: PythonResolvedString,
    ): Boolean {

        //
        // Obtain the normalized compile-time value used for inspection.
        //
        val value =
            resolved.resolvedString.trim()

        //
        // Reject empty resolved values.
        //
        if (value.isEmpty())
            return false

        //
        // Reject values retaining unresolved interpolation-style braces.
        //
        if ('{' in value || '}' in value)
            return false

        //
        // Reject leading-dot values such as hidden or special filenames.
        //
        if (value.startsWith("."))
            return false

        //
        // Reject glob patterns representing collections rather than resources.
        //
        if (isGlobPattern(value))
            return false

        //
        // Apply the normal Resource Navigator classification rules.
        //
        return shouldHandle(
            literal,
            value,
        )
    }

    /**
     * Return true if the supplied string represents a supported HTTP or HTTPS
     * URL.
     *
     * URL Classification
     * ------------------
     *
     * +--------------------------------------+----------------------+--------+
     * | Value                                | URI Scheme           | Result |
     * +--------------------------------------+----------------------+--------+
     * | "https://example.com"                | https                | true   |
     * | "http://example.com/file.pdf"        | http                 | true   |
     * | "HTTPS://example.com"                | https (normalized)   | true   |
     * | "ftp://example.com/file.pdf"         | ftp                  | false  |
     * | "mailto:user@example.com"            | mailto               | false  |
     * | "docs/manual.pdf"                    | None                 | false  |
     * | "manual.pdf"                         | None                 | false  |
     * +--------------------------------------+----------------------+--------+
     *
     * Behavior
     * --------
     * • Parses the supplied value as a URI.
     * • Extracts and normalizes the URI scheme to lowercase.
     * • Accepts only schemes contained in urlSchemes.
     * • Returns false when the value has no URI scheme.
     * • Returns false when URI parsing fails.
     *
     * This function performs URL classification only. It does not verify that
     * the remote resource exists or is reachable.
     */
    fun isUrl(
        value: String,
    ): Boolean =

        //
        // Parse the value as a URI and test its normalized scheme.
        // Treat malformed or otherwise unparseable values as non-URLs.
        //
        runCatching {
            URI(value).scheme
                ?.lowercase() in urlSchemes
        }.getOrDefault(false)

    /**
     * Return true if the supplied string appears to represent a filesystem
     * glob pattern rather than one concrete resource path.
     *
     * Glob Pattern Detection
     * ----------------------
     *
     * +--------------------------------------+--------------------------+--------+
     * | Value                                | Detection                | Result |
     * +--------------------------------------+--------------------------+--------+
     * | "*.pdf"                              | Contains *               | true   |
     * | "docs/*.pdf"                         | Contains *               | true   |
     * | "docs/**/manual.pdf"                 | Contains *               | true   |
     * | "image?.png"                         | Contains ?               | true   |
     * | "docs/file?.pdf"                     | Contains ?               | true   |
     * | "docs/manual.pdf"                    | No glob characters       | false  |
     * | "C:\Docs\manual.pdf"                 | No glob characters       | false  |
     * +--------------------------------------+--------------------------+--------+
     *
     * Behavior
     * --------
     * • Detects the '*' wildcard used to match arbitrary character sequences.
     * • Detects the '?' wildcard used to match a single arbitrary character.
     * • Treats any value containing either character as a glob pattern.
     *
     * Glob patterns describe sets of potentially matching resources rather
     * than one concrete navigation target and are therefore excluded from
     * Resource Navigator navigation and missing-resource inspection.
     *
     * This is intentionally a lightweight heuristic rather than a complete
     * filesystem-glob parser.
     */
     */
    private fun isGlobPattern(
        value: String,
    ): Boolean =

        //
        // Detect supported filesystem glob wildcard characters.
        //
        value.contains('*') ||
                value.contains('?')

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

        //
        // Isolate the filename from URL suffixes and path components.
        //
        val filename =
            value
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .trim('"', '\'')

        //
        // Extract and normalize the final filename extension.
        //
        return filename
            .substringAfterLast('.', "")
            .lowercase()
    }

    /**
     * Determine whether a string resembles an absolute filesystem path.
     *
     * Absolute Path Heuristics
     * ------------------------
     *
     * +--------------------------------------+--------------------------+--------+
     * | Value                                | Detection                | Result |
     * +--------------------------------------+--------------------------+--------+
     * | "C:\Docs\manual.pdf"                 | Windows drive path       | true   |
     * | "C:\Docs"                            | Windows drive path       | true   |
     * | "C:/Docs/manual.pdf"                 | Windows drive path       | true   |
     * | "\\server\share\docs"                | UNC path                 | true   |
     * | "/home/user/docs"                    | Unix absolute path       | true   |
     * | "docs/manual.pdf"                    | Relative path            | false  |
     * | "docs"                               | Relative value           | false  |
     * +--------------------------------------+--------------------------+--------+
     *
     * This function performs syntactic classification only. It does not access
     * the filesystem or determine whether the path exists.
     */
    private fun looksLikeAbsolutePath(
        value: String,
    ): Boolean =

        //
        // Recognize Unix absolute paths, Windows drive paths, and UNC paths.
        //
        value.startsWith("/") ||
                Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(value) ||
                value.startsWith("\\\\")

    /**
     * Heuristically determine whether a string resembles a filesystem path.
     *
     * Filesystem Path Heuristics
     * --------------------------
     *
     * +--------------------------------------+----------------------------------+--------+
     * | Value                                | Detection                        | Result |
     * +--------------------------------------+----------------------------------+--------+
     * | "docs/manual.pdf"                    | Forward slash + extension        | true   |
     * | "/home/user/manual.pdf"              | Forward slash                   | true   |
     * | "C:\Docs\manual.pdf"                 | Backslash + drive prefix         | true   |
     * | "manual.pdf"                         | Filename extension               | true   |
     * | "C:"                                 | Windows drive prefix             | true   |
     * | "docs"                               | No path-like syntax              | false  |
     * | "manual"                             | No path-like syntax              | false  |
     * | "data:text/plain,hello"              | Explicitly excluded URI scheme   | false  |
     * | "mailto:user@example.com"            | Explicitly excluded URI scheme   | false  |
     * +--------------------------------------+----------------------------------+--------+
     *
     * Behavior
     * --------
     * • Rejects explicitly unsupported data: and mailto: URI schemes.
     * • Detects Unix-style path separators.
     * • Detects Windows-style path separators.
     * • Treats values containing a period as potentially filename-like.
     * • Detects Windows drive prefixes such as C: or D:.
     *
     * This function performs only inexpensive syntactic classification. It does
     * not validate path syntax, determine whether the path is absolute or
     * relative, or access the filesystem to determine whether the resource
     * exists.
     *
     * The heuristic is intentionally permissive. Final resource acceptance is
     * controlled by shouldHandle(), while filesystem resolution and existence
     * checks are performed by ResourceResolver.
     */
    fun looksLikeFilePath(
        value: String,
    ): Boolean {

        //
        // Reject URI schemes that must not be interpreted as filesystem paths.
        //
        if (
            value.startsWith("data:") ||
            value.startsWith("mailto:")
        ) {
            return false
        }

        //
        // Accept values containing common filesystem-path characteristics.
        //
        return value.contains('/') ||
                value.contains('\\') ||
                value.contains('.') ||
                Regex("^[A-Za-z]:").containsMatchIn(value)
    }
}