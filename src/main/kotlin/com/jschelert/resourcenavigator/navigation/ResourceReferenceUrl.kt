package com.jschelert.resourcenavigator.navigation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.jetbrains.python.psi.PyStringLiteralExpression

/**
 * =================================================================================================
 * ResourceReferenceUrl
 * =================================================================================================
 *
 * URL PSI Reference
 * -----------------
 * Represents a navigable HTTP or HTTPS URL contained within a Python string
 * literal.
 *
 * Behavior
 * --------
 * • Marks a URL string as a navigable PSI reference.
 * • Defines the clickable text range within the string literal.
 * • Delegates all navigation behavior to ResourceGotoDeclarationHandler.
 * • Does not resolve to a physical PSI element.
 *
 * Responsibilities
 * ----------------
 * • Represent URL references within IntelliJ's PSI model.
 * • Provide the clickable reference region.
 * • Participate in IntelliJ reference discovery.
 *
 * Dependencies
 * ------------
 * • PyStringLiteralExpression
 * • PsiReferenceBase
 * • TextRange
 *
 * See Also
 * --------
 * • ResourceReferenceLocal
 * • ResourceReferenceContributor
 * • ResourceGotoDeclarationHandler
 * • ResourceDispatcher
 *
 * Architectural Notes
 * -------------------
 * • URLs have no corresponding PSI declaration, so resolve() intentionally
 *   returns null.
 *
 * • Actual navigation is coordinated by ResourceGotoDeclarationHandler and
 *   ultimately delegated to ResourceDispatcher, ensuring that all URL-opening
 *   behavior remains centralized.
 */
class ResourceReferenceUrl(
    element: PyStringLiteralExpression,
    range: TextRange,
) : PsiReferenceBase<PyStringLiteralExpression>(
    element,
    range,
    true,
) {

    /**
     * URL references do not resolve to a PSI declaration.
     */
    override fun resolve(): PsiElement? =
        null

    /**
     * URL references do not participate in code completion.
     */
    override fun getVariants(): Array<Any> =
        emptyArray()
}