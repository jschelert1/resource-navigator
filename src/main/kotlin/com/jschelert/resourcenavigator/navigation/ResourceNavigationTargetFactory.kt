package com.jschelert.resourcenavigator.navigation

import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.util.ResourceExtensionRegistry
import com.jschelert.resourcenavigator.util.ResourceKind
import com.jschelert.resourcenavigator.util.ResourceTarget

/**
 * =================================================================================================
 * ResourceNavigationTargetFactory
 * =================================================================================================
 *
 * Navigation Target Factory
 * -------------------------
 * Creates the synthetic PSI declaration targets displayed by IntelliJ's
 * Choose Declaration popup for a resolved resource.
 *
 * Behavior
 * --------
 * • Creates one or more navigation targets depending upon the resource type.
 * • URLs produce a browser navigation target.
 * • Directories produce a native-folder navigation target.
 * • Browser-oriented local files produce IDE and Browser targets.
 * • External resources produce IDE and External Application targets.
 * • IDE-oriented resources produce an IDE navigation target.
 *
 * Responsibilities
 * ----------------
 * • Construct ResourceNavigationElement instances.
 * • Determine available navigation modes.
 * • Handle directory navigation independently of extension policy.
 * • Generate user-visible labels and locations.
 * • Isolate navigation target construction from navigation execution.
 *
 * Dependencies
 * ------------
 * • ResourceNavigationElement
 * • ResourceExtensionRegistry
 * • ResourceTarget
 * • ResourceKind
 * • ResourceOpenMode
 *
 * See Also
 * --------
 * • ResourceGotoDeclarationHandler
 * • ResourceDispatcher
 * • ResourceNavigationElement
 * • ResourceExtensionRegistry
 *
 * Architectural Notes
 * -------------------
 * • This factory determines what navigation choices should be presented to
 *   the user. It does not actually perform navigation.
 *
 * • Directories bypass extension-based navigation policy and are represented
 *   by a dedicated folder-opening target.
 *
 * • Navigation execution is delegated to ResourceDispatcher through
 *   ResourceNavigationElement, keeping construction and execution as separate
 *   responsibilities.
 *
 * • ResourceExtensionRegistry centralizes extension-based navigation policy
 *   for browser, IDE, and external-application resources.
 */
object ResourceNavigationTargetFactory {

    /**
     * Create the declaration target presented to the user for a resolved
     * resource.
     *
     * Behavior
     * --------
     * • URLs receive a browser navigation target.
     * • Directories receive a native-folder navigation target.
     * • Browser-oriented resources receive a Browser target.
     * • External resources receive an External Application target.
     * • IDE-oriented resources receive an IDE navigation target.
     *
     * Navigation Examples
     * -------------------
     *
     * +----------------------+---------------------------+
     * | Resource             | Navigation                |
     * +----------------------+---------------------------+
     * | .html / .mhtml       | Open in Browser           |
     * | .pdf                 | Open Externally           |
     * | .docx                | Open in Word              |
     * | .xlsx                | Open in Excel             |
     * | .png / .svg          | Open Externally           |
     * | .py / .json          | Open in PyCharm           |
     * | Directory            | Open Folder               |
     * +----------------------+---------------------------+
     */
    fun createTargets(
        source: PyStringLiteralExpression,
        target: ResourceTarget,
    ): Array<PsiElement> {

        //
        // Create direct browser navigation for URL resources.
        //
        if (target.kind == ResourceKind.URL) {
            return arrayOf(
                navigationTarget(
                    source = source,
                    target = target,
                    mode = ResourceOpenMode.BROWSER,
                    label = "Open in Browser",
                    location = target.sourceValue,
                )
            )
        }

        //
        // Obtain the resolved local virtual file.
        //
        val file =
            target.virtualFile
                ?: return emptyArray()

        //
        // Determine the resolved resource location.
        //
        val location =
            target.resolvedPath
                ?: file.path

        //
        // Directories are IDE resources and do not participate in
        // extension-based open-mode selection.
        //
        if (file.isDirectory) {
            return arrayOf(
                navigationTarget(
                    source = source,
                    target = target,
                    mode = ResourceOpenMode.IDE,
                    label = "Open Folder",
                    location = location,
                )
            )
        }

        //
        // Determine the resource file extension.
        //
        val extension =
            file.extension.orEmpty()

        //
        // Determine the configured open mode for this resource type.
        //
        val mode =
            ResourceExtensionRegistry.openMode(extension)

        //
        // Create navigation targets appropriate for the configured open mode.
        //
        return when (mode) {

            ResourceOpenMode.BROWSER ->
                arrayOf(
                    navigationTarget(
                        source = source,
                        target = target,
                        mode = ResourceOpenMode.BROWSER,
                        label = "Open in Browser",
                        location = location,
                    ),
                )

            ResourceOpenMode.EXTERNAL ->
                arrayOf(
                    navigationTarget(
                        source = source,
                        target = target,
                        mode = ResourceOpenMode.EXTERNAL,
                        label = ResourceExtensionRegistry.externalLabel(extension),
                        location = location,
                    ),
                )

            ResourceOpenMode.IDE ->
                arrayOf(
                    navigationTarget(
                        source = source,
                        target = target,
                        mode = ResourceOpenMode.IDE,
                        label = "Open in PyCharm",
                        location = location,
                    ),
                )
        }
    }

    /**
     * Create a single synthetic navigation target for the Choose Declaration
     * dialog.
     */
    private fun navigationTarget(
        source: PyStringLiteralExpression,
        target: ResourceTarget,
        mode: ResourceOpenMode,
        label: String,
        location: String,
    ): PsiElement =
        ResourceNavigationElement(
            source = source,
            target = target,
            mode = mode,
            label = label,
            location = location,
        )
}