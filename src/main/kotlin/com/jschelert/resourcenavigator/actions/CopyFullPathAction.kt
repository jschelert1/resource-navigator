package com.jschelert.resourcenavigator.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import java.awt.datatransfer.StringSelection

/**
 * =================================================================================================
 * CopyFullPathAction
 * =================================================================================================
 *
 * IDE Action
 * ----------
 * Copies the fully resolved filesystem path or URL of the currently selected
 * Resource Navigator target to the system clipboard.
 *
 * This action never performs path resolution itself.
 * All resource resolution is delegated to ResourceActionSupport.
 *
 * Behavior
 * --------
 * • Local resources copy the resolved absolute filesystem path.
 * • URL resources copy the original URL.
 * • The action is only enabled when a valid resource target exists.
 *
 * Responsibilities
 * ----------------
 * • Obtain the current ResourceTarget.
 * • Determine the appropriate path or URL.
 * • Copy the value to the system clipboard.
 *
 * Dependencies
 * ------------
 * • ResourceActionSupport
 * • CopyPasteManager
 *
 * See Also
 * --------
 * • OpenResourceAction
 * • RevealInFileManagerAction
 */
class CopyFullPathAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread =
        ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            ResourceActionSupport.target(e)?.let {
                it.resolvedPath != null || it.sourceValue.startsWith("http")
            } == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val target = ResourceActionSupport.target(e)
            ?: return

        CopyPasteManager
            .getInstance()
            .setContents(
                StringSelection(
                    target.resolvedPath ?: target.sourceValue
                )
            )
    }
}