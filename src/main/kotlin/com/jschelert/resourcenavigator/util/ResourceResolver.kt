package com.jschelert.resourcenavigator.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.config.ResourceNavigatorSettings
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * =================================================================================================
 * ResourceResolver
 * =================================================================================================
 *
 * Resource Resolver
 * -----------------
 * Resolves classified resource values into immutable ResourceTarget objects
 * representing URLs, existing local resources, or missing local resources.
 *
 * Resource resolution preserves both the original source-oriented string value
 * and the reconstructed compile-time value used for classification and
 * filesystem resolution.
 *
 * Behavior
 * --------
 * • Resolves supported HTTP and HTTPS URLs.
 * • Resolves absolute filesystem paths.
 * • Resolves relative paths against the source-file directory.
 * • Resolves relative paths against the project root.
 * • Expands '~' to the current user's home directory.
 * • Supports Windows drive-letter and UNC absolute paths.
 * • Locates existing resources through IntelliJ LocalFileSystem.
 * • Produces ResourceTarget objects for both existing and missing local resources.
 * • Preserves source and resolved string representations independently.
 *
 * Resolution Flow
 * ---------------
 *
 *     PyStringLiteralExpression
 *              +
 *     PythonResolvedString
 *              |
 *              v
 *          resolve()
 *              |
 *              +----> determine sourceValue
 *              |
 *              +----> determine resolvedValue
 *              |
 *              v
 *     ResourceClassifier.shouldHandle()
 *              |
 *        +-----+------+
 *        |            |
 *        v            v
 *       URL        Local resource
 *        |            |
 *        v            v
 *   ResourceTarget  resolveLocal()
 *                         |
 *                         v
 *                  expandHome()
 *                         |
 *                         v
 *                  candidatePaths()
 *                         |
 *             +-----------+-----------+
 *             |                       |
 *             v                       v
 *       source directory         project root
 *             |                       |
 *             +-----------+-----------+
 *                         |
 *                         v
 *                 LocalFileSystem
 *                         |
 *                +--------+--------+
 *                |                 |
 *                v                 v
 *             existing          missing
 *                |                 |
 *                v                 v
 *          ResourceTarget     ResourceTarget
 *
 * Source and Resolved Values
 * --------------------------
 *
 * +--------------------------------------+--------------------------------------+---------------------------+
 * | Python Source                        | sourceValue                          | resolvedValue             |
 * +--------------------------------------+--------------------------------------+---------------------------+
 * | "docs/manual.pdf"                    | docs/manual.pdf                      | docs/manual.pdf           |
 * | "docs/" "manual.pdf"                 | source-oriented literal contents    | docs/manual.pdf           |
 * | f"docs/{'manual'}.pdf"               | source-oriented f-string contents   | docs/manual.pdf           |
 * | BASE = "docs/"; BASE + "manual.pdf"  | source-oriented expression          | evaluated constant value  |
 * +--------------------------------------+--------------------------------------+---------------------------+
 *
 * sourceValue preserves the representation associated with the originating PSI
 * source, while resolvedValue represents the semantic compile-time value used
 * for classification and resource resolution.
 *
 * This distinction is intentionally retained because IntelliJ navigation and
 * Ctrl-hover highlighting have been observed to depend on source-oriented
 * information for some multiline and adjacent Python string literals.
 *
 * Local Path Resolution
 * ---------------------
 *
 * +--------------------------------------+--------------------------------------+---------------------------+
 * | Resource Value                       | Resolution Strategy                  | Target                    |
 * +--------------------------------------+--------------------------------------+---------------------------+
 * | C:\Docs\manual.pdf                   | Windows absolute path                | Direct                    |
 * | \\server\share\manual.pdf            | UNC absolute path                    | Direct                    |
 * | /home/user/manual.pdf                | Absolute path                        | Direct                    |
 * | ~/Docs/manual.pdf                    | Expand user home                     | Home-relative             |
 * | docs/manual.pdf                      | Source directory / project root      | Setting dependent         |
 * | docs/missing.pdf                     | Candidate resolution fails           | Missing ResourceTarget    |
 * +--------------------------------------+--------------------------------------+---------------------------+
 *
 * Responsibilities
 * ----------------
 * • Convert classified resource values into ResourceTarget objects.
 * • Preserve source and compile-time resolved representations.
 * • Distinguish URL resources from local filesystem resources.
 * • Generate candidate filesystem paths for relative resources.
 * • Apply configured relative-path resolution strategies.
 * • Normalize supported filesystem paths.
 * • Locate IntelliJ VirtualFiles for existing resources.
 * • Preserve a preferred resolved path for missing resources.
 *
 * Dependencies
 * ------------
 * • PythonResolvedString
 * • PythonStringUtil
 * • ResourceClassifier
 * • ResourceNavigatorSettings
 * • ResourceTarget
 * • ResourceKind
 * • LocalFileSystem
 * • java.nio.file.Path
 *
 * Architectural Notes
 * -------------------
 * • Python expression evaluation is owned by PythonStringResolver.
 *   ResourceResolver consumes PythonResolvedString rather than evaluating
 *   compile-time Python expressions itself.
 *
 * • Resource classification is delegated to ResourceClassifier before normal
 *   resolve() processing continues.
 *
 * • resolveLocal() intentionally performs local filesystem resolution directly
 *   and may also be used by callers that have already established that a value
 *   represents a local resource, such as bracket-citation navigation.
 *
 * • Relative-path resolution order is deterministic. The source-file directory
 *   is considered before the project root when both strategies are enabled.
 *
 * • Existing resources are represented by ResourceTarget objects containing an
 *   IntelliJ VirtualFile and normalized resolved path.
 *
 * • Missing resources still produce ResourceTarget objects so inspections and
 *   documentation can report the intended path without requiring the resource
 *   to exist.
 *
 * • ResourceResolver does not construct PSI references, navigation targets, or
 *   perform resource opening. Those responsibilities remain delegated to the
 *   navigation subsystem.
 *
 * See Also
 * --------
 * • PythonStringResolver
 * • PythonResolvedString
 * • PythonStringUtil
 * • ResourceClassifier
 * • ResourceTarget
 * • ResourceReferenceContributor
 * • ResourceNavigationTargetFactory
 * • ResourceDispatcher
 *
 * Revision History
 * ----------------
 * v2.0.0 — 2026-07-23 (JS)
 * • Added sourceValue and resolvedValue separation through PythonResolvedString.
 * • Added resolution of reconstructed compile-time Python string values while
 *   preserving source-oriented values for PSI and navigation behavior.
 * • Retained source-based fallback resolution for navigation paths requiring
 *   original literal correspondence.
 * • Added centralized local-resource resolution through resolveLocal().
 * • Added deterministic source-directory and project-root candidate generation.
 * • Added home-directory expansion and Windows absolute/UNC path handling.
 * • Preserved ResourceTarget creation for missing resources to support
 *   inspection and Quick Documentation.
 */
object ResourceResolver {

    /**
     * Resolve a Python string literal into a navigable resource target.
     *
     * Resolution Modes
     * ----------------
     *
     * +----------------------------+-------------------------+-------------------------+
     * | Invocation                 | sourceValue             | resolvedValue           |
     * +----------------------------+-------------------------+-------------------------+
     * | resolve(literal)           | Original source content | Same as sourceValue     |
     * | resolve(literal, resolved) | resolved.sourceString   | resolved.resolvedString |
     * +----------------------------+-------------------------+-------------------------+
     *
     * When a PythonResolvedString is supplied, its source and reconstructed
     * compile-time values are preserved independently. The reconstructed value
     * is used for resource classification and resolution.
     *
     * When no resolved value is supplied, the original source-oriented literal
     * contents are used for both representations. This fallback is intentionally
     * retained because IntelliJ Ctrl-hover and Go To Declaration behavior for
     * some multiline and adjacent Python string literals has been observed to
     * depend on preserving the original source representation.
     *
     * The optional resolved argument therefore provides compile-time expression
     * support without removing the source-preserving resolution path required
     * by existing IntelliJ navigation behavior.
     */
    fun resolve(
        element: PyStringLiteralExpression,
        resolved: PythonResolvedString? = null,
    ): ResourceTarget? {

        //
        // Preserve the supplied source representation or fall back to the
        // original source-oriented literal contents.
        //
        val sourceValue =
            resolved?.sourceString
                ?: PythonStringUtil.contentText(element)

        //
        // Use the reconstructed compile-time value when available; otherwise
        // use the source value for legacy/source-sensitive resolution.
        //
        val resolvedValue =
            resolved?.resolvedString
                ?: sourceValue

        //
        // Emit resource-resolution diagnostics when global diagnostics are enabled.
        //
        if (ResourceNavigatorSettings.DIAGNOSTICS_ENABLED) {
            diagnoseResolve(
                element = element,
                resolved = resolved,
                sourceValue = sourceValue,
                resolvedValue = resolvedValue,
            )
        }

        //
        // Reject values that do not qualify as navigable resource candidates.
        //
        if (!ResourceClassifier.shouldHandle(element, resolvedValue))
            return null

        //
        // Construct URL targets directly without filesystem resolution.
        //
        if (ResourceClassifier.isUrl(resolvedValue)) {
            return ResourceTarget(
                sourceValue = sourceValue,
                resolvedValue = resolvedValue,
                kind = ResourceKind.URL,
            )
        }

        //
        // Resolve all remaining candidates as local filesystem resources.
        //
        return resolveLocal(
            project = element.project,
            containingFile = element.containingFile,
            sourceValue = sourceValue,
            resolvedValue = resolvedValue,
        )
    }

    /**
     * Resolve a local resource value against the available filesystem
     * resolution candidates.
     *
     * Local Resolution
     * ----------------
     *
     * +--------------------------------------+--------------------------------------+------------------------+
     * | Resolved Value                       | Candidate Strategy                   | Result                 |
     * +--------------------------------------+--------------------------------------+------------------------+
     * | "C:\Docs\manual.pdf"                 | Absolute path                        | Existing / missing     |
     * | "\\server\share\manual.pdf"          | UNC absolute path                    | Existing / missing     |
     * | "/home/user/manual.pdf"              | Absolute path                        | Existing / missing     |
     * | "~/Docs/manual.pdf"                  | Expand home, then resolve            | Existing / missing     |
     * | "docs/manual.pdf"                    | Source directory / project root      | First existing target  |
     * | "docs/missing.pdf"                   | Source directory / project root      | Missing target         |
     * +--------------------------------------+--------------------------------------+------------------------+
     *
     * Behavior
     * --------
     * • Trims the resolved semantic value.
     * • Expands a leading '~' to the current user's home directory.
     * • Generates candidate paths according to the configured resolution rules.
     * • Searches candidate paths in deterministic order.
     * • Uses IntelliJ LocalFileSystem to locate existing resources.
     * • Returns immediately when the first existing resource is found.
     * • Returns a missing ResourceTarget when no candidate exists.
     * • Preserves sourceValue and resolvedValue independently in the target.
     *
     * When no candidate exists, the first generated candidate is retained as
     * the preferred resolved path. This allows inspections and Quick
     * Documentation to report the intended resource location even though the
     * resource does not currently exist.
     *
     * This function performs local-resource resolution only. Classification of
     * ordinary Python resource literals is normally performed by resolve()
     * before this function is called. Callers such as bracket-citation handling
     * may invoke resolveLocal() directly after establishing local-resource
     * context independently.
     */
    fun resolveLocal(
        project: Project,
        containingFile: PsiFile?,
        sourceValue: String,
        resolvedValue: String = sourceValue,
    ): ResourceTarget {

        //
        // Normalize whitespace and expand the user's home-directory prefix.
        //
        val normalized =
            expandHomePath(resolvedValue.trim())

        //
        // Generate filesystem candidates in configured resolution order.
        //
        val candidatePaths =
            candidatePaths(
                project = project,
                file = containingFile,
                value = normalized,
            )

        //
        // Obtain IntelliJ's local filesystem service for VirtualFile lookup.
        //
        val localFileSystem =
            LocalFileSystem.getInstance()

        //
        // Search candidate paths and return the first existing resource.
        //
        candidatePaths.forEach { path ->

            //
            // Refresh the filesystem state and locate the candidate VirtualFile.
            //
            val virtualFile =
                localFileSystem.refreshAndFindFileByNioFile(path)

            //
            // Construct an existing-resource target when the candidate resolves.
            //
            if (virtualFile != null) {
                return target(
                    sourceValue = sourceValue,
                    resolvedValue = resolvedValue,
                    path = path,
                    file = virtualFile,
                )
            }
        }

        //
        // Preserve the highest-priority candidate as the intended missing path.
        //
        val preferred =
            candidatePaths.firstOrNull()

        //
        // Return a missing-resource target when no candidate currently exists.
        //
        return ResourceTarget(
            sourceValue = sourceValue,
            resolvedValue = resolvedValue,
            kind = ResourceKind.LOCAL_FILE,
            resolvedPath = preferred?.normalize()?.toString(),
            exists = false,
        )
    }

    /**
     * Generate the ordered filesystem paths that should be tested for a local
     * resource value.
     *
     * Candidate Path Generation
     * -------------------------
     *
     * +----------------------+----------------------+----------------------+----------------------------+
     * | Resource Value       | Source Dir Enabled   | Project Root Enabled | Candidate Paths            |
     * +----------------------+----------------------+----------------------+----------------------------+
     * | C:\Docs\file.pdf     | Either               | Either               | Absolute path only         |
     * | \\server\file.pdf    | Either               | Either               | Absolute path only         |
     * | /home/user/file.pdf  | Either               | Either               | Absolute path only         |
     * | docs/file.pdf        | Yes                  | No                   | Source directory           |
     * | docs/file.pdf        | No                   | Yes                  | Project root               |
     * | docs/file.pdf        | Yes                  | Yes                  | Source dir, project root   |
     * | docs/file.pdf        | No                   | No                   | Empty                      |
     * | invalid path         | Either               | Either               | Empty                      |
     * +----------------------+----------------------+----------------------+----------------------------+
     *
     * Behavior
     * --------
     * • Parses the supplied value into a java.nio.file.Path.
     * • Returns no candidates when the value cannot be parsed.
     * • Returns absolute paths directly without applying relative-path rules.
     * • Recognizes Windows drive-letter and UNC absolute paths explicitly.
     * • Resolves relative paths against the source-file directory when enabled.
     * • Resolves relative paths against the project root when enabled.
     * • Preserves source-directory-before-project-root candidate precedence.
     * • Normalizes every generated candidate path.
     * • Eliminates duplicate candidate paths while preserving insertion order.
     *
     * When both relative-path strategies are enabled, the source-file directory
     * is intentionally added first and therefore has resolution precedence over
     * the project root. resolveLocal() subsequently tests candidates in this
     * same order and returns the first existing resource.
     *
     * A LinkedHashSet-backed linkedSetOf is used so duplicate paths are removed
     * without losing deterministic candidate ordering.
     */
    private fun candidatePaths(
        project: Project,
        file: PsiFile?,
        value: String,
    ): List<Path> {

        //
        // Parse the supplied resource value into a filesystem path.
        //
        val parsed =
            parsePath(value)
                ?: return emptyList()

        //
        // Return absolute paths directly without relative-path expansion.
        //
        if (parsed.isAbsolute || isWindowsPathAbsolute(value))
            return listOf(parsed.normalize())

        //
        // Load the configured relative-path resolution strategies.
        //
        val settings =
            ResourceNavigatorSettings.getInstance().state

        //
        // Preserve candidate precedence while eliminating duplicate paths.
        //
        val candidates =
            linkedSetOf<Path>()

        /*
         * --------------------------------------------------
         * Source-directory resolution
         * --------------------------------------------------
         */

        //
        // Resolve relative paths against the containing source-file directory.
        //
        if (settings.resolveAgainstSourceDirectory) {

            file?.virtualFile?.parent?.let { parent ->

                candidates.add(
                    Paths.get(parent.path)
                        .resolve(parsed)
                        .normalize()
                )
            }
        }

        /*
         * --------------------------------------------------
         * Project-root resolution
         * --------------------------------------------------
         */

        //
        // Resolve relative paths against the current project root.
        //
        if (settings.resolveAgainstProjectRoot) {

            project.basePath?.let { basePath ->

                candidates.add(
                    Paths.get(basePath)
                        .resolve(parsed)
                        .normalize()
                )
            }
        }

        //
        // Return candidates in their deterministic resolution order.
        //
        return candidates.toList()
    }

    /**
     * Parse a resource value into a java.nio.file.Path.
     *
     * Path Parsing
     * ------------
     *
     * +------------------------------+------------------------------+------------------+
     * | Input                        | Normalized Input             | Result           |
     * +------------------------------+------------------------------+------------------+
     * | "docs/manual.pdf"            | Platform separators         | Path             |
     * | "docs/sub/file.txt"          | Platform separators         | Path             |
     * | "C:\Docs\manual.pdf"         | Unchanged on Windows        | Path             |
     * | "C:/Docs/manual.pdf"         | Windows separators          | Path             |
     * | Invalid filesystem path      | Platform separators         | null             |
     * +------------------------------+------------------------------+------------------+
     *
     * Behavior
     * --------
     * • Converts forward slashes to the current platform's file separator.
     * • Parses the normalized value using java.nio.file.Paths.
     * • Returns the resulting Path when parsing succeeds.
     * • Returns null when the value cannot be represented as a valid Path.
     *
     * Forward-slash normalization allows resource strings to use portable path
     * syntax while still producing paths appropriate for the current operating
     * system.
     *
     * This function performs syntactic path parsing only. It does not determine
     * whether the resulting path is absolute, whether the resource exists, or
     * how a relative path should be resolved.
     */
    private fun parsePath(
        value: String,
    ): Path? =
        try {

            //
            // Normalize portable forward slashes to the platform separator.
            //
            Paths.get(
                value.replace(
                    '/',
                    java.io.File.separatorChar,
                )
            )

        } catch (_: InvalidPathException) {

            //
            // Treat values that cannot form filesystem paths as unresolvable.
            //
            null
        }

    /**
     * Return true if the supplied value represents a Windows-style absolute
     * filesystem path.
     *
     * Windows Absolute-Path Detection
     * -------------------------------
     *
     * +--------------------------------------+--------------------------+--------+
     * | Value                                | Detection                | Result |
     * +--------------------------------------+--------------------------+--------+
     * | "C:\Docs\manual.pdf"                 | Drive + backslash        | true   |
     * | "C:/Docs/manual.pdf"                 | Drive + forward slash    | true   |
     * | "D:\file.txt"                        | Drive + backslash        | true   |
     * | "\\server\share\manual.pdf"          | UNC prefix               | true   |
     * | "docs\manual.pdf"                    | Relative Windows path    | false  |
     * | "C:manual.pdf"                       | Drive-relative path      | false  |
     * | "/home/user/manual.pdf"              | Non-Windows absolute     | false  |
     * | "manual.pdf"                         | Relative path            | false  |
     * +--------------------------------------+--------------------------+--------+
     *
     * Behavior
     * --------
     * • Recognizes drive-letter paths beginning with a drive designation
     *   followed by either '\' or '/'.
     * • Recognizes UNC paths beginning with '\\'.
     * • Rejects drive-relative forms such as "C:manual.pdf".
     * • Does not attempt to recognize non-Windows absolute-path syntax.
     *
     * This check supplements Path.isAbsolute because Windows-style absolute
     * paths may need to be recognized independently of the host platform's
     * java.nio.file.Path semantics.
     *
     * The function performs syntax recognition only. It does not parse,
     * normalize, resolve, or verify the existence of the path.
     */
    private fun isWindowsPathAbsolute(
        value: String,
    ): Boolean =

        //
        // Detect drive-letter absolute paths or UNC network paths.
        //
        Regex("^[A-Za-z]:[\\\\/].+").matches(value) ||
                value.startsWith("\\\\")

    /**
     * Expand a leading '~' home-directory marker to the current user's home
     * directory.
     *
     * Home-Directory Expansion
     * ------------------------
     *
     * +------------------------------+------------------------------------------+
     * | Input                        | Result                                   |
     * +------------------------------+------------------------------------------+
     * | "~"                          | Current user's home directory            |
     * | "~/docs/manual.pdf"          | <home>/docs/manual.pdf                   |
     * | "~\docs\manual.pdf"          | <home>\docs\manual.pdf                   |
     * | "docs/manual.pdf"            | Unchanged                                |
     * | "/home/user/manual.pdf"      | Unchanged                                |
     * | "C:\Docs\manual.pdf"         | Unchanged                                |
     * +------------------------------+------------------------------------------+
     *
     * Behavior
     * --------
     * • Expands a value consisting only of '~' to the user.home system property.
     * • Expands values beginning with '~/' or '~\' relative to the user's home
     *   directory.
     * • Preserves the original path separator following '~'.
     * • Returns all other values unchanged.
     *
     * This function performs only leading home-directory expansion. It does not
     * normalize the resulting path, resolve relative path components, or verify
     * that the resulting resource exists.
     */
    private fun expandHomePath(
        value: String,
    ): String {

        //
        // Expand a standalone home-directory marker.
        //
        if (value == "~")
            return System.getProperty("user.home")

        //
        // Expand home-relative paths using either path-separator convention.
        //
        if (
            value.startsWith("~/") ||
            value.startsWith("~\\")
        ) {
            return System.getProperty("user.home") +
                    value.substring(1)
        }

        //
        // Preserve values that do not use the supported home-directory syntax.
        //
        return value
    }

    /**
     * Construct a ResourceTarget for an existing local filesystem resource.
     *
     * Target Construction
     * -------------------
     *
     * +------------------+-----------------------------------------------+
     * | Field            | Value                                         |
     * +------------------+-----------------------------------------------+
     * | sourceValue      | Original source-oriented resource value       |
     * | resolvedValue    | Reconstructed semantic resource value         |
     * | kind             | ResourceKind.LOCAL_FILE                       |
     * | virtualFile      | Located IntelliJ VirtualFile                  |
     * | resolvedPath     | Normalized resolved filesystem path           |
     * | exists           | true                                          |
     * +------------------+-----------------------------------------------+
     *
     * Behavior
     * --------
     * • Preserves the source and resolved resource values independently.
     * • Identifies the target as a local filesystem resource.
     * • Associates the target with the located IntelliJ VirtualFile.
     * • Normalizes the resolved filesystem path before storing it.
     * • Marks the resource as existing.
     *
     * This helper is used only after local filesystem resolution has
     * successfully located a VirtualFile. Missing resources are constructed
     * separately by resolveLocal() with exists set to false.
     */
    private fun target(
        sourceValue: String,
        resolvedValue: String,
        path: Path,
        file: VirtualFile,
    ) =

    //
    // Construct the immutable target for an existing local resource.
        //
        ResourceTarget(
            sourceValue = sourceValue,
            resolvedValue = resolvedValue,
            kind = ResourceKind.LOCAL_FILE,
            virtualFile = file,
            resolvedPath = path.normalize().toString(),
            exists = true,
        )

    /**
     * Print diagnostic information comparing the source-oriented Python string
     * representation with the reconstructed compile-time value used by resource
     * resolution.
     *
     * Intended for debugging differences between PSI source text,
     * PythonStringUtil content extraction, PythonStringResolver evaluation,
     * ResourceClassifier behavior, and final resource resolution.
     *
     * This diagnostic also preserves the information used to investigate
     * multiline and adjacent-literal Ctrl-hover and Go To Declaration behavior.
     */
    private fun diagnoseResolve(
        element: PyStringLiteralExpression,
        resolved: PythonResolvedString?,
        sourceValue: String,
        resolvedValue: String,
    ) {
        //
        // Obtain the legacy/source-oriented content representation.
        //
        val contentTextValue =
            PythonStringUtil.contentText(element)

        //
        // Evaluate the classification components independently.
        //
        val pathWrapped =
            PythonResourceContext.isPathWrapped(element)

        val extension =
            ResourceClassifier.extension(resolvedValue)

        val settings =
            ResourceNavigatorSettings.getInstance()

        val recognizedExtension =
            extension in settings.extensions()

        val looksLikePath =
            ResourceClassifier.looksLikeFilePath(resolvedValue)

        val shouldHandle =
            ResourceClassifier.shouldHandle(
                element,
                resolvedValue,
            )

        //
        // Print source and resolved representations.
        //
        println()
        println("===== Resource Resolver =====")
        println("literal.text = <${element.text}>")
        println("literal.textLength = ${element.textLength}")
        println("contentText = <$contentTextValue>")
        println("contentText.length = ${contentTextValue.length}")
        println("resolved supplied = ${resolved != null}")
        println("sourceValue = <$sourceValue>")
        println("sourceValue.length = ${sourceValue.length}")
        println("resolvedValue = <$resolvedValue>")
        println("resolvedValue.length = ${resolvedValue.length}")
        println("source == resolved = ${sourceValue == resolvedValue}")

        //
        // Print resource-classification state.
        //
        println("pathWrapped = $pathWrapped")
        println("extension = $extension")
        println("recognizedExtension = $recognizedExtension")
        println("looksLikePath = $looksLikePath")
        println("shouldHandle = $shouldHandle")
    }

}