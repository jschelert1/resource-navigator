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
 * Resolves navigable resources referenced from Python string literals into
 * immutable ResourceTarget model objects.
 *
 * Behavior
 * --------
 * • Resolves HTTP and HTTPS URLs.
 * • Resolves absolute filesystem paths.
 * • Resolves relative paths against the source directory.
 * • Resolves relative paths against the project root.
 * • Expands '~' to the user's home directory.
 * • Produces ResourceTarget objects for existing and missing resources.
 *
 * Responsibilities
 * ----------------
 * • Resolve resource strings.
 * • Generate candidate filesystem paths.
 * • Locate IntelliJ VirtualFiles.
 * • Construct immutable ResourceTarget objects.
 *
 * Dependencies
 * ------------
 * • PythonStringUtil
 * • ResourceClassifier
 * • ResourceNavigatorSettings
 * • ResourceTarget
 * • ResourceKind
 * • LocalFileSystem
 *
 * See Also
 * --------
 * • ResourceClassifier
 * • PythonStringUtil
 * • PythonResourceContext
 * • ResourceReferenceContributor
 * • ResourceDispatcher
 *
 * Architectural Notes
 * -------------------
 * • ResourceResolver performs path resolution only.
 *
 * • Classification is delegated to ResourceClassifier.
 *
 * • Python string parsing is delegated to PythonStringUtil.
 *
 * • Navigation and PSI reference construction are intentionally outside the
 *   scope of this class.
 */
object ResourceResolver {

    fun resolve(
        element: PyStringLiteralExpression,
    ): ResourceTarget? {

        val value =
            PythonStringUtil.contentText(element)

        if (!ResourceClassifier.shouldHandle(element, value))
            return null

        if (ResourceClassifier.isUrl(value)) {
            return ResourceTarget(
                rawValue = value,
                kind = ResourceKind.URL,
            )
        }

        return resolveLocal(
            project = element.project,
            containingFile = element.containingFile,
            raw = value,
        )
    }

    fun resolveLocal(
        project: Project,
        containingFile: PsiFile?,
        raw: String,
    ): ResourceTarget {

        val normalized = expandHome(raw.trim())

        val candidatePaths = candidatePaths(
            project = project,
            file = containingFile,
            value = normalized,
        )

        val localFileSystem =
            LocalFileSystem.getInstance()

        candidatePaths.forEach { path ->

            val virtualFile =
                localFileSystem.refreshAndFindFileByNioFile(path)

            if (virtualFile != null) {
                return target(
                    raw = raw,
                    path = path,
                    file = virtualFile,
                )
            }
        }

        val preferred =
            candidatePaths.firstOrNull()

        return ResourceTarget(
            rawValue = raw,
            kind = ResourceKind.LOCAL_FILE,
            resolvedPath = preferred?.normalize()?.toString(),
            exists = false,
        )
    }

    private fun candidatePaths(
        project: Project,
        file: PsiFile?,
        value: String,
    ): List<Path> {

        val parsed =
            parsePath(value)
                ?: return emptyList()

        if (parsed.isAbsolute || isWindowsAbsolute(value))
            return listOf(parsed.normalize())

        val settings =
            ResourceNavigatorSettings.getInstance().state

        val candidates =
            linkedSetOf<Path>()

        if (settings.resolveAgainstSourceDirectory) {

            file?.virtualFile?.parent?.let { parent ->

                candidates.add(
                    Paths.get(parent.path)
                        .resolve(parsed)
                        .normalize()
                )
            }
        }

        if (settings.resolveAgainstProjectRoot) {

            project.basePath?.let { basePath ->

                candidates.add(
                    Paths.get(basePath)
                        .resolve(parsed)
                        .normalize()
                )
            }
        }

        return candidates.toList()
    }

    private fun parsePath(
        value: String,
    ): Path? =
        try {

            Paths.get(
                value.replace(
                    '/',
                    java.io.File.separatorChar,
                )
            )

        } catch (_: InvalidPathException) {
            null
        }

    private fun isWindowsAbsolute(
        value: String,
    ): Boolean =
        Regex("^[A-Za-z]:[\\\\/].+").matches(value) ||
                value.startsWith("\\\\")

    private fun expandHome(
        value: String,
    ): String {

        if (value == "~")
            return System.getProperty("user.home")

        if (
            value.startsWith("~/") ||
            value.startsWith("~\\")
        ) {
            return System.getProperty("user.home") +
                    value.substring(1)
        }

        return value
    }

    private fun target(
        raw: String,
        path: Path,
        file: VirtualFile,
    ) = ResourceTarget(
        rawValue = raw,
        kind = ResourceKind.LOCAL_FILE,
        virtualFile = file,
        resolvedPath = path.normalize().toString(),
        exists = true,
    )
}