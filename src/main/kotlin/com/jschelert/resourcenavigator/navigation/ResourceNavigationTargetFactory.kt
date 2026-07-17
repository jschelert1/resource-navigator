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
 * • Browser-oriented local files produce IDE and Browser targets.
 * • External-document resources produce IDE and External Application targets.
 * • All other resources produce an IDE navigation target.
 *
 * Responsibilities
 * ----------------
 * • Construct ResourceNavigationElement instances.
 * • Determine available navigation modes.
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
 * • Navigation execution is delegated to ResourceDispatcher through
 *   ResourceNavigationElement, keeping construction and execution as separate
 *   responsibilities.
 *
 * • ResourceExtensionRegistry centralizes extension policies so that the same
 *   browser and external-application rules are applied consistently
 *   throughout the plugin.
 */
object ResourceNavigationTargetFactory {

    /**
     * Create the declaration targets presented to the user for a resolved
     * resource.
     *
     * Behavior
     * --------
     * • URLs receive a browser navigation target.
     * • Browser resources receive IDE and Browser targets.
     * • External resources receive IDE and External Application targets.
     * • Remaining resources receive an IDE navigation target.
     */
    fun createTargets(
        source: PyStringLiteralExpression,
        target: ResourceTarget,
    ): Array<PsiElement> {

        if (target.kind == ResourceKind.URL) {
            return arrayOf(
                navigationTarget(
                    source = source,
                    target = target,
                    mode = ResourceOpenMode.BROWSER,
                    label = "Open in Browser",
                    location = target.rawValue,
                )
            )
        }

        val file =
            target.virtualFile
                ?: return emptyArray()

        val extension =
            file.extension.orEmpty()

        val location =
            target.resolvedPath
                ?: file.path

        val mode =
            ResourceExtensionRegistry.openMode(extension)

        return when (mode) {

            ResourceOpenMode.BROWSER ->
                arrayOf(
                    navigationTarget(
                        source = source,
                        target = target,
                        mode = ResourceOpenMode.IDE,
                        label = "Open in PyCharm",
                        location = location,
                    ),
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
                        mode = ResourceOpenMode.IDE,
                        label = "Open in PyCharm",
                        location = location,
                    ),
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