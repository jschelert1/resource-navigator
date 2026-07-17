package com.jschelert.resourcenavigator.navigation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.util.ResourceResolver

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
 * • Resolves ordinary resource strings using ResourceResolver.
 * • Supports bracketed resource citations through an explicitly supplied
 *   resource value.
 * • Resolves the resource to either a PSI file or PSI directory.
 * • Resource opening behavior is delegated to the navigation subsystem.
 *
 * Responsibilities
 * ----------------
 * • Represent a local filesystem PSI reference.
 * • Resolve the referenced resource.
 * • Convert VirtualFiles into PSI elements.
 * • Provide IntelliJ navigation compatibility.
 *
 * Dependencies
 * ------------
 * • ResourceResolver
 * • PsiManager
 * • PsiReferenceBase
 *
 * See Also
 * --------
 * • ResourceReferenceUrl
 * • ResourceGotoDeclarationHandler
 * • ResourceNavigationTargetFactory
 * • ResourceDispatcher
 *
 * Architectural Notes
 * -------------------
 * • ResourceReferenceLocal and ResourceReferenceUrl intentionally provide
 *   separate PSI reference implementations because they resolve different
 *   resource types.
 *
 * • If substantial common behavior develops between the two classes, consider
 *   introducing an AbstractResourceReference base class. Until then, keeping
 *   the implementations independent avoids unnecessary abstraction.
 *
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

    override fun resolve(): PsiElement? {

        val target =
            if (rawValue != null) {
                ResourceResolver.resolveLocal(
                    project = element.project,
                    containingFile = element.containingFile,
                    raw = rawValue,
                )
            } else {
                ResourceResolver.resolve(element)
                    ?: return null
            }

        val file =
            target.virtualFile
                ?: return null

        val psiManager =
            PsiManager.getInstance(
                element.project,
            )

        return psiManager.findFile(file)
            ?: psiManager.findDirectory(file)
    }

    override fun getVariants(): Array<Any> =
        emptyArray()
}