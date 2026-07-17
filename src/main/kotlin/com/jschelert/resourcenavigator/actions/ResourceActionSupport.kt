package com.jschelert.resourcenavigator.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyStringLiteralExpression
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
     * Resolve the resource target beneath the current editor caret.
     */
    fun target(
        event: AnActionEvent,
    ): ResourceTarget? {

        val editor =
            event.getData(CommonDataKeys.EDITOR)
                ?: return null

        val file =
            event.getData(CommonDataKeys.PSI_FILE)
                ?: return null

        val offset =
            editor.caretModel.offset

        val leaf =
            file.findElementAt(offset)
                ?: file.findElementAt((offset - 1).coerceAtLeast(0))
                ?: return null

        val literal =
            PsiTreeUtil.getParentOfType(
                leaf,
                PyStringLiteralExpression::class.java,
                false,
            ) ?: return null

        return ResourceResolver.resolve(literal)
    }
}