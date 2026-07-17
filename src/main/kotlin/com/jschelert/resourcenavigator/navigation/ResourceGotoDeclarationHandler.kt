package com.jschelert.resourcenavigator.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.util.ResourceKind
import com.jschelert.resourcenavigator.util.ResourceResolver

/**
 * =================================================================================================
 * ResourceGotoDeclarationHandler
 * =================================================================================================
 *
 * Go To Declaration Handler
 * -------------------------
 * Implements Ctrl+Click and Go to Declaration navigation for resources
 * referenced from Python string literals.
 *
 * Behavior
 * --------
 * • Locates the enclosing Python string literal beneath the caret.
 * • Gives precedence to bracketed resource citations.
 * • Falls back to ordinary one-resource-per-string navigation.
 * • Resolves resources before creating navigation targets.
 * • Prevents navigation to missing local resources.
 *
 * Responsibilities
 * ----------------
 * • Integrate Resource Navigator with the IntelliJ Go to Declaration API.
 * • Dispatch between citation and ordinary resource navigation.
 * • Delegate resource resolution and target creation.
 *
 * Dependencies
 * ------------
 * • ResourceCitationParser
 * • ResourceResolver
 * • ResourceNavigationTargetFactory
 * • ResourceKind
 * • PyStringLiteralExpression
 * • PsiTreeUtil
 *
 * See Also
 * --------
 * • ResourceReferenceContributor
 * • ResourceNavigationTargetFactory
 * • ResourceDispatcher
 * • ResourceReferenceLocal
 * • ResourceReferenceUrl
 *
 * Architectural Notes
 * -------------------
 * • Bracketed citations intentionally take precedence over ordinary resource
 *   literals so that multiple independently navigable resources may coexist
 *   within a single Python string.
 *
 * • Resource resolution and target creation are delegated to their respective
 *   subsystems. This class coordinates navigation flow rather than performing
 *   resource parsing or resolution itself.
 */
class ResourceGotoDeclarationHandler : GotoDeclarationHandler {

    /**
     * Return the navigation targets for the resource beneath the caret.
     *
     * Behavior
     * --------
     * • Attempts bracketed citation navigation first.
     * • Falls back to ordinary resource navigation.
     * • Returns null when no valid navigation target exists.
     */
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor,
    ): Array<PsiElement>? {

        val literal =
            findStringLiteral(sourceElement)
                ?: return null

        /*
         * --------------------------------------------------
         * Bracket citation
         * --------------------------------------------------
         */
        ResourceCitationParser.findCitation(
            literal,
            offset,
        )?.let { citation ->

            val target =
                ResourceResolver.resolveLocal(
                    project = literal.project,
                    containingFile = literal.containingFile,
                    raw = citation.text,
                )

            if (
                target.kind == ResourceKind.LOCAL_FILE &&
                !target.exists
            ) {
                return null
            }

            return ResourceNavigationTargetFactory.createTargets(
                source = literal,
                target = target,
            )
        }

        /*
         * --------------------------------------------------
         * Ordinary resource literal
         * --------------------------------------------------
         */
        val target =
            ResourceResolver.resolve(literal)
                ?: return null

        if (
            target.kind == ResourceKind.LOCAL_FILE &&
            !target.exists
        ) {
            return null
        }

        return ResourceNavigationTargetFactory.createTargets(
            source = literal,
            target = target,
        )
    }

    /**
     * Return the action text displayed by the IDE.
     */
    override fun getActionText(
        context: DataContext,
    ): String =
        "Open Resource"

    /**
     * Locate the Python string literal associated with the supplied PSI
     * element.
     */
    private fun findStringLiteral(
        sourceElement: PsiElement?,
    ): PyStringLiteralExpression? {

        sourceElement ?: return null

        return sourceElement as? PyStringLiteralExpression
            ?: PsiTreeUtil.getParentOfType(
                sourceElement,
                PyStringLiteralExpression::class.java,
                false,
            )
    }
}