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
 * • Rejects separator-only strings that do not identify a concrete resource.
 * • Rejects descriptive f-strings that embed a resource inside surrounding prose.
 * • Provides policy-driven filtering for missing-resource inspection.
 * • Separates permissive positive navigation from conservative negative inspection.
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
 * • Exclude separator-only strings such as "\" and "\\".
 * • Distinguish standalone resource f-strings from descriptive f-string prose.
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
 *              +----> universal inspection exclusions
 *              |
 *              v
 *     MissingResourcePolicy
 *              |
 *              +----> CONSERVATIVE -> false
 *              |
 *              +----> BALANCED -> strong filesystem structure
 *              |
 *              +----> AGGRESSIVE -> shouldHandle()
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
 * v2.2.2 — 2026-10-02 (JS)
 * • Simplified PSI-aware missing-resource inspection to one exclusion gate and one policy dispatch.
 * • Aggressive mode now delegates directly to shouldHandle() after universal inspection exclusions.
 *
 * v2.2.1 — 2026-10-02 (JS)
 * • Added inspection-only rejection for standalone textual escape/control fragments such as "\\n" and "\\t".
 * • Preserved legitimate Windows relative paths and permissive positive navigation behavior.
 *
 * v2.2.0 — 2026-10-02 (JS)
 * • Added Conservative, Balanced, and Aggressive missing-resource inspection policies.
 * • Added pure value/policy inspection classification for unit testing.
 * • Preserved permissive navigation classification independently from missing-resource policy.
 *
 * v2.1.0 — 2026-10-02 (JS)
 * • Added conservative missing-resource rejection for command-line switches such
 *   as "/F" and "/IM" without changing normal absolute-path navigation.
 *
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
 * • Added rejection of descriptive interpolated strings such as
 *   f"Generate {RESOURCE}" while preserving standalone resource f-strings such as
 *   f"{RESOURCE}", f"docs/{NAME}.pdf", and f"{BASE}/docs/{NAME}.pdf".
 * • Added rejection of separator-only strings so escaped backslash literals do
 *   not become false-positive local resource candidates.
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
        // Reject values composed only of filesystem separators. A slash or
        // escaped backslash sequence alone does not identify a concrete resource.
        //
        if (isSeparatorOnlyPath(value)) {
            return false
        }

        //
        // Reject interpolated strings whose source form is descriptive prose
        // containing a resource rather than a standalone resource expression.
        //
        if (isDescriptiveFString(element)) {
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
     * Universal inspection exclusions are applied once before policy dispatch.
     * Conservative rejects inferred resources, Balanced requires strong filesystem
     * structure, and Aggressive delegates to the ordinary PSI-aware navigation
     * classifier so pathlib and configured navigation behavior remain available.
     */
    fun shouldInspectMissing(
        literal: PyStringLiteralExpression,
        resolved: PythonResolvedString,
    ): Boolean {

        val value =
            resolved.resolvedString.trim()

        //
        // Correctness guards apply before every policy decision. Explicit bracketed
        // resources are handled by MissingResourceInspection before this classifier.
        //
        if (isMissingInspectionExcluded(value))
            return false

        return when (
            ResourceNavigatorSettings.getInstance().state.missingResourcePolicy
        ) {

            ResourceNavigatorSettings.MissingResourcePolicy.CONSERVATIVE ->
                false

            ResourceNavigatorSettings.MissingResourcePolicy.BALANCED ->
                looksLikeStrongFilesystemPath(value)

            ResourceNavigatorSettings.MissingResourcePolicy.AGGRESSIVE ->
                shouldHandle(
                    literal,
                    value,
                )
        }
    }

    /**
     * Determine whether a string value is sufficiently resource-like to justify a
     * missing-resource diagnostic under the supplied confidence policy.
     *
     * This overload is intentionally PSI- and filesystem-independent so the negative
     * classification policy can be tested directly.
     */
    fun shouldInspectMissing(
        value: String,
        policy: ResourceNavigatorSettings.MissingResourcePolicy,
    ): Boolean {

        val normalized =
            value.trim()

        //
        // Correctness guards apply in every policy mode.
        //
        if (isMissingInspectionExcluded(normalized))
            return false

        return when (policy) {

            ResourceNavigatorSettings.MissingResourcePolicy.CONSERVATIVE ->
                false

            ResourceNavigatorSettings.MissingResourcePolicy.BALANCED ->
                looksLikeStrongFilesystemPath(normalized)

            ResourceNavigatorSettings.MissingResourcePolicy.AGGRESSIVE ->
                looksLikeFilePath(normalized) ||
                        looksLikeAbsolutePath(normalized)
        }
    }

    /**
     * Return true for values that must never generate inferred missing-resource
     * diagnostics, regardless of confidence policy.
     */
    private fun isMissingInspectionExcluded(
        value: String,
    ): Boolean =
        value.isEmpty() ||
                value.contains('\n') ||
                value.contains('\r') ||
                isEscapeControlFragment(value) ||
                '{' in value ||
                '}' in value ||
                value.startsWith(".") ||
                isGlobPattern(value) ||
                isSeparatorOnlyPath(value) ||
                isCommandLineSwitch(value)

    /**
     * Return true when a value has enough filesystem structure for Balanced
     * missing-resource inspection.
     *
     * Bare filenames remain ambiguous. Relative paths require an explicit separator.
     * Windows drive paths and UNC paths are strong. Unix absolute paths require at
     * least two non-empty path components so shallow API fragments such as
     * "/json/version" remain silent while "/home/user/file.json" is accepted.
     */
    private fun looksLikeStrongFilesystemPath(
        value: String,
    ): Boolean {

        if (Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(value))
            return true

        if (value.startsWith("\\\\"))
            return value.removePrefix("\\\\")
                .split('\\')
                .count { it.isNotEmpty() } >= 2

        if (value.startsWith("/"))
            return value.split('/')
                .count { it.isNotEmpty() } >= 3

        return value.contains('/') ||
                value.contains('\\')
    }

    /**
     * Return true when the entire value is a textual escape/control fragment rather
     * than a filesystem path.
     *
     * This guard is intentionally narrow and inspection-only. It recognizes standalone
     * backslash escape tokens such as "\n" and "\t" while preserving legitimate
     * Windows relative paths such as "docs\missing.pdf" and "folder\name.txt".
     */
    private fun isEscapeControlFragment(
        value: String,
    ): Boolean =
        Regex("""^\\[abfnrtv]$""").matches(value)

    /**
     * Return true when the supplied value has the compact shape of a Windows-style
     * command-line switch rather than a concrete Unix filesystem path.
     *
     * The guard is intentionally inspection-only and conservative: a leading slash
     * followed by one alphanumeric option token is rejected, while values containing
     * another path separator remain eligible as ordinary absolute paths.
     */
    private fun isCommandLineSwitch(
        value: String,
    ): Boolean =
        Regex("^/[A-Za-z0-9][A-Za-z0-9_-]*$").matches(value)

    /**
     * Return true when the supplied value consists only of filesystem path
     * separators and therefore cannot identify a concrete resource.
     *
     * Separator-Only Detection
     * ------------------------
     *
     * +----------------------+-----------------------------+--------+
     * | Value                | Interpretation              | Result |
     * +----------------------+-----------------------------+--------+
     * | "\"                  | Separator only              | true   |
     * | "\\"                 | Separator only              | true   |
     * | "/"                  | Separator only              | true   |
     * | "//"                 | Separator only              | true   |
     * | "docs\manual.pdf"    | Concrete relative path      | false  |
     * | "C:\Temp"            | Concrete absolute path      | false  |
     * | "\\server\share"     | Concrete UNC path           | false  |
     * +----------------------+-----------------------------+--------+
     *
     * This guard is intentionally narrow. It rejects only non-empty strings made
     * entirely from slash and backslash characters, preserving normal Windows,
     * UNC, Unix, and relative resource paths.
     */
    private fun isSeparatorOnlyPath(
        value: String,
    ): Boolean =
        value.isNotEmpty() &&
                value.all {
                    it == '/' ||
                            it == '\\'
                }

    /**
     * Return true when an interpolated Python string is descriptive prose rather
     * than a standalone resource expression.
     *
     * F-String Resource Shape
     * -----------------------
     *
     * +--------------------------------------+--------------------------------------+--------+
     * | Python Source                        | Interpretation                       | Result |
     * +--------------------------------------+--------------------------------------+--------+
     * | f"{RESOURCE}"                        | Pure resource interpolation          | false  |
     * | f"docs/{NAME}.pdf"                   | Resource path construction           | false  |
     * | f"{BASE}/docs/{NAME}.pdf"            | Resource path construction           | false  |
     * | f"Generate {RESOURCE}"               | Descriptive prose + resource         | true   |
     * | f"Writing file {RESOURCE}"           | Descriptive prose + resource         | true   |
     * +--------------------------------------+--------------------------------------+--------+
     *
     * The check operates on the source-oriented literal text rather than the
     * resolved value. This preserves legitimate spaces inside resource names
     * while rejecting ordinary prose that precedes the first interpolation.
     *
     * A non-empty literal prefix is considered resource construction when it
     * already has path syntax or terminates at a path boundary. Otherwise it is
     * treated as descriptive prose.
     */
    private fun isDescriptiveFString(
        element: PyStringLiteralExpression,
    ): Boolean {

        //
        // Ordinary Python strings are never descriptive f-strings.
        //
        if (!element.isInterpolated)
            return false

        //
        // Obtain the source-oriented literal contents without quote/prefix syntax.
        //
        val source =
            PythonStringUtil.contentText(element)
                .trim()

        //
        // Locate the first actual interpolation marker in the source text.
        //
        val interpolationIndex =
            source.indexOf('{')

        if (interpolationIndex < 0)
            return false

        //
        // Pure interpolation has no descriptive prefix.
        //
        val prefix =
            source.substring(0, interpolationIndex)

        if (prefix.isEmpty())
            return false

        //
        // A prefix ending at a path separator or containing explicit path syntax
        // is part of resource construction rather than descriptive prose.
        //
        if (
            prefix.endsWith("/") ||
            prefix.endsWith("\\") ||
            prefix.contains('/') ||
            prefix.contains('\\') ||
            Regex("^[A-Za-z]:").containsMatchIn(prefix)
        ) {
            return false
        }

        //
        // A remaining whitespace-delimited prefix is ordinary descriptive text.
        //
        return prefix.any { it.isWhitespace() }
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
