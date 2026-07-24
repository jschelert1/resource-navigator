package com.jschelert.resourcenavigator.navigation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
//import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.util.PythonStringResolver
import com.jschelert.resourcenavigator.util.ResourceResolver
import com.jschelert.resourcenavigator.util.PythonResourceContext

/**
 * =================================================================================================
 * ResourceReferenceLocal
 * =================================================================================================
 *
 * PSI Reference
 * -------------
 * Provides a PSI reference for local filesystem resources recognized by
 * Resource Navigator.
 *
 * Behavior
 * --------
 * • Resolves ordinary Python resource strings through PythonStringResolver
 *   and ResourceResolver.
 * • Supports bracketed resource citations through an explicitly supplied
 *   resource value.
 * • Resolves local resources to either a PSI file or PSI directory.
 * • Returns null when the resource cannot be resolved to a VirtualFile.
 * • Resource opening behavior is delegated to the navigation subsystem.
 *
 * Responsibilities
 * ----------------
 * • Represent a local filesystem PSI reference.
 * • Resolve ordinary Python strings into local resource targets.
 * • Resolve explicitly supplied bracket-citation values.
 * • Convert resolved VirtualFiles into IntelliJ PSI elements.
 * • Provide IntelliJ navigation compatibility.
 *
 * Dependencies
 * ------------
 * • PythonStringResolver
 * • ResourceResolver
 * • PsiManager
 * • PsiReferenceBase
 *
 * Resolution Flow
 * ---------------
 *
 * Ordinary Python resources are evaluated before filesystem resolution:
 *
 *     PyStringLiteralExpression
 *              |
 *              v
 *     PythonStringResolver
 *              |
 *              v
 *     PythonResolvedString
 *              |
 *              v
 *     ResourceResolver
 *              |
 *              v
 *     ResourceTarget.virtualFile
 *              |
 *              v
 *     PsiManager
 *              |
 *              v
 *     PsiFile / PsiDirectory
 *
 * Bracketed citations already provide an independently parsed resource value
 * and therefore bypass Python string evaluation:
 *
 *     rawValue
 *        |
 *        v
 *     ResourceResolver.resolveLocal()
 *        |
 *        v
 *     ResourceTarget.virtualFile
 *        |
 *        v
 *     PsiManager
 *        |
 *        v
 *     PsiFile / PsiDirectory
 *
 * Architectural Notes
 * -------------------
 * • Ordinary resources and bracketed citations intentionally follow separate
 *   resolution paths. Ordinary Python strings are evaluated through
 *   PythonStringResolver before resource resolution, while bracketed citations
 *   provide their parsed resource value directly to ResourceResolver.
 *
 * • The final ResourceTarget VirtualFile is converted through PsiManager into
 *   either a PSI file or PSI directory, allowing IntelliJ's standard PSI
 *   navigation infrastructure to handle the resulting target.
 *
 * • ResourceReferenceLocal and ResourceReferenceUrl intentionally provide
 *   separate PSI reference implementations because they resolve different
 *   resource types.
 *
 * • Resource opening behavior remains outside this class. This reference is
 *   responsible for resolving a local resource into a PSI navigation target,
 *   while opening behavior is handled by the navigation subsystem.
 *
 * • If substantial common behavior develops between ResourceReferenceLocal
 *   and ResourceReferenceUrl, consider introducing an AbstractResourceReference
 *   base class. Until then, keeping the implementations independent avoids
 *   unnecessary abstraction.
 *
 * See Also
 * --------
 * • PythonStringResolver
 * • ResourceResolver
 * • ResourceReferenceUrl
 * • ResourceGotoDeclarationHandler
 * • ResourceNavigationTargetFactory
 * • ResourceDispatcher
 *
 * Revision History
 * ----------------
 * v2.0.0 — 2026-07-23 (JS)
 * • Updated ordinary local-resource resolution to evaluate Python strings
 *   through PythonStringResolver before invoking ResourceResolver.
 * • Preserved direct local resolution for bracketed citations supplied through
 *   rawValue.
 * • Separated Python string evaluation from filesystem resource resolution in
 *   the ordinary PSI reference path.
 * • Extended ordinary resource resolution to evaluate the complete supported
 *   compile-time expression through PythonResourceContext.
 * • Changed local PSI references to resolve to synthetic
 *   ResourceNavigationElement targets rather than underlying PsiFile or
 *   PsiDirectory elements, preserving Resource Navigator navigation policy.
 * • Documented the separate ordinary-resource and bracket-citation resolution
 *   flows.
 */

class ResourceReferenceLocal(
    element: PyStringLiteralExpression,
    range: TextRange,
    private val rawValue: String? = null,
) : PsiReferenceBase<PyStringLiteralExpression>(
    element,
    range,
    true,
) {

    /**
     * Resolve the local resource reference to a Resource Navigator synthetic
     * navigation element.
     *
     * Returning a synthetic navigation element allows Resource Navigator to
     * retain control of resource opening behavior after IntelliJ resolves the
     * PSI reference. Returning the underlying PsiFile or PsiDirectory directly
     * would instead invoke IntelliJ's default IDE navigation and bypass the
     * Resource Navigator navigation policy.
     *
     * Resolution and navigation therefore follow this flow:
     *
     *     ResourceReferenceLocal
     *              │
     *              ▼
     *     resolve ResourceTarget
     *              │
     *              ▼
     *     ResourceNavigationTargetFactory
     *              │
     *              ▼
     *     ResourceNavigationElement
     *              │
     *              ▼
     *          navigate()
     *              │
     *              ▼
     *     ResourceDispatcher
     *              │
     *              ├── xlsx → Excel
     *              ├── svg  → external viewer
     *              ├── html → browser
     *              ├── py   → PyCharm
     *              └── dir  → native file manager
     *
     * Ordinary resources are first evaluated through their complete supported
     * compile-time expression, while bracketed citations continue to resolve
     * directly from their explicitly supplied resource value.
     */
    override fun resolve(): PsiElement? {

        //
        // Resolve the local resource through the appropriate input path.
        //
        val target =
            if (rawValue != null) {

                //
                // Resolve an explicitly supplied bracket-citation value.
                //
                ResourceResolver.resolveLocal(
                    project = element.project,
                    containingFile = element.containingFile,
                    sourceValue = rawValue,
                )
            } else {

                //
                // Determine the complete supported expression represented by
                // the ordinary resource literal.
                //
                val expression =
                    PythonResourceContext.evaluationExpression(element)

                //
                // Evaluate the complete expression into its compile-time value.
                //
                val resolved =
                    PythonStringResolver.resolve(expression)
                        ?: return null

                //
                // Resolve the evaluated expression as a local resource.
                //
                ResourceResolver.resolve(
                    element,
                    resolved,
                ) ?: return null
            }

        //
        // Reject resources that do not resolve to an existing local target.
        //
        if (!target.exists || target.virtualFile == null)
            return null

        //
        // Create the synthetic navigation target whose navigation behavior is
        // controlled by Resource Navigator rather than IntelliJ's default PSI
        // file navigation.
        //
        return ResourceNavigationTargetFactory
            .createTargets(
                source = element,
                target = target,
            )
            .singleOrNull()
    }

    /**
     * Return no code-completion variants for this resource reference.
     *
     * Resource Navigator provides navigation for recognized resources but
     * does not currently contribute completion suggestions.
     */
    override fun getVariants(): Array<Any> =
        emptyArray()
}