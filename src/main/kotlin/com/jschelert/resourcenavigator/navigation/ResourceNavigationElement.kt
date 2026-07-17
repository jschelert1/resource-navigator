package com.jschelert.resourcenavigator.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.util.ResourceTarget

/**
 * =================================================================================================
 * ResourceNavigationElement
 * =================================================================================================
 *
 * Synthetic Navigation Element
 * ----------------------------
 * Represents a synthetic PSI declaration target displayed within IntelliJ's
 * Choose Declaration popup. Selecting one of these entries delegates the
 * navigation request to ResourceDispatcher using the associated navigation
 * mode.
 *
 * Behavior
 * --------
 * • Appears as a selectable declaration target in the IDE.
 * • Displays a user-friendly label and location string.
 * • Delegates navigation to ResourceDispatcher.
 * • Represents navigation choices rather than real source code.
 *
 * Responsibilities
 * ----------------
 * • Provide synthetic PSI elements for navigation.
 * • Preserve the originating Python string literal.
 * • Store the resolved resource and desired navigation mode.
 * • Delegate resource opening when selected.
 *
 * Dependencies
 * ------------
 * • ResourceDispatcher
 * • ResourceTarget
 * • ResourceOpenMode
 * • FakePsiElement
 * • PyStringLiteralExpression
 *
 * See Also
 * --------
 * • ResourceNavigationTargetFactory
 * • ResourceDispatcher
 * • ResourceGotoDeclarationHandler
 * • ResourceReferenceLocal
 * • ResourceReferenceUrl
 *
 * Architectural Notes
 * -------------------
 * • ResourceNavigationElement intentionally represents a synthetic PSI element
 *   rather than a physical source file. This allows the standard IntelliJ
 *   Choose Declaration dialog to present multiple navigation options (IDE,
 *   Browser, External, etc.) while reusing the existing navigation
 *   infrastructure.
 *
 * • Actual resource opening is delegated entirely to ResourceDispatcher so
 *   that all navigation policies remain centralized in one location.
 */
class ResourceNavigationElement(
    private val source: PyStringLiteralExpression,
    private val target: ResourceTarget,
    private val mode: ResourceOpenMode,
    private val label: String,
    private val location: String,
) : FakePsiElement() {

    /**
     * Return the originating PSI element.
     */
    override fun getParent(): PsiElement =
        source

    /**
     * Return the display name shown within the Choose Declaration dialog.
     */
    override fun getName(): String =
        label

    /**
     * Return the primary presentation text displayed by the IDE.
     */
    override fun getPresentableText(): String =
        label

    /**
     * Return the secondary location text displayed beneath the label.
     */
    override fun getLocationString(): String =
        location

    /**
     * Delegate navigation to ResourceDispatcher using the configured
     * navigation mode.
     */
    override fun navigate(
        requestFocus: Boolean,
    ) {
        ResourceDispatcher.open(
            project = source.project,
            target = target,
            mode = mode,
        )
    }

    /**
     * This synthetic element always supports navigation.
     */
    override fun canNavigate(): Boolean =
        true

    /**
     * Navigation targets are synthetic rather than physical source
     * declarations, so source navigation is not applicable.
     */
    override fun canNavigateToSource(): Boolean =
        false
}