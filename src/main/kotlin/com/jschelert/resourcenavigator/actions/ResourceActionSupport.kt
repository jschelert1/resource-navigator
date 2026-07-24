package com.jschelert.resourcenavigator.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.util.PythonStringResolver
import com.jschelert.resourcenavigator.util.ResourceResolver
import com.jschelert.resourcenavigator.util.ResourceTarget

/**
 * =================================================================================================
 * ResourceActionSupport
 * =================================================================================================
 *
 * Action Helper
 * -------------
 * Shared utility methods used by Resource Navigator editor actions.
 *
 * Behavior
 * --------
 * • Locates the Python string literal beneath the current editor caret.
 * • Resolves the literal into a ResourceTarget.
 * • Returns null when no valid resource target exists.
 *
 * Responsibilities
 * ----------------
 * • Obtain the active editor and PSI file.
 * • Locate the leaf PSI element beneath the caret.
 * • Recover the enclosing PyStringLiteralExpression.
 * • Resolve the literal using ResourceResolver.
 *
 * Dependencies
 * ------------
 * • CommonDataKeys
 * • PsiTreeUtil
 * • ResourceResolver
 *
 * See Also
 * --------
 * • OpenResourceAction
 * • RevealInFileManagerAction
 * • CopyFullPathAction
 */
object ResourceActionSupport {

    /**
     * Resolve the ResourceTarget beneath the current editor caret.
     *
     * Workflow
     * --------
     * • Obtain the active editor and PSI file.
     * • Locate the PSI element beneath the caret.
     * • Recover the enclosing Python string literal.
     * • Resolve the Python string into a constant value.
     * • Resolve the constant into a ResourceTarget.
     *
     * Returns
     * -------
     * Returns the resolved ResourceTarget, or null if no supported
     * resource exists beneath the current caret position.
     */
    fun target(
        event: AnActionEvent,
    ): ResourceTarget? {

        //
        // Obtain the active editor and PSI file.
        //
        val editor =
            event.getData(CommonDataKeys.EDITOR)
                ?: return null

        val file =
            event.getData(CommonDataKeys.PSI_FILE)
                ?: return null

        //
        // Locate the PSI element beneath the caret.
        //
        val offset =
            editor.caretModel.offset

        val leaf =
            file.findElementAt(offset)
                ?: file.findElementAt((offset - 1).coerceAtLeast(0))
                ?: return null

        //
        // Recover the enclosing Python string literal.
        //
        val literal =
            PsiTreeUtil.getParentOfType(
                leaf,
                PyStringLiteralExpression::class.java,
                false,
            ) ?: return null

        //
        // Resolve the Python string into a constant value.
        //
        val resolved =
            PythonStringResolver.resolve(literal)
                ?: return null

        //
        // Resolve the constant into a ResourceTarget.
        //
        return ResourceResolver.resolve(
            literal,
            resolved,
        )
    }
}