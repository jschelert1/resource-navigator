package com.jschelert.resourcenavigator.actions

import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.jschelert.resourcenavigator.util.ResourceKind
import java.io.File

/**
 * =================================================================================================
 * RevealResourceAction
 * =================================================================================================
 *
 * IDE Action
 * ----------
 * Reveals the currently selected local resource within the operating system's
 * native file manager.
 *
 * Behavior
 * --------
 * • Local filesystem resources are revealed in the platform file manager.
 * • URL resources are not supported by this action.
 * • The action is only enabled when a resolved local filesystem resource exists.
 *
 * Responsibilities
 * ----------------
 * • Obtain the current ResourceTarget.
 * • Verify that the target is a resolved local file.
 * • Delegate the reveal operation to the IntelliJ Platform.
 *
 * Dependencies
 * ------------
 * • ResourceActionSupport
 * • RevealFileAction
 * • ResourceKind
 *
 * See Also
 * --------
 * • OpenResourceAction
 * • CopyFullPathAction
 * • ResourceDispatcher
 */
class RevealResourceAction : AnAction(), DumbAware {

    /**
     * Perform action updates on IntelliJ's background thread.
     */
    override fun getActionUpdateThread(): ActionUpdateThread =
        ActionUpdateThread.BGT

    /**
     * Enable this action when the current resource can be revealed
     * in the operating system's native file manager.
     */
    override fun update(
        e: AnActionEvent,
    ) {

        val target =
            ResourceActionSupport.target(e)

        e.presentation.isEnabledAndVisible =
            target?.let {
                it.kind == ResourceKind.LOCAL_FILE &&
                        it.resolvedPath != null
            } == true
    }

    /**
     * Reveal the currently selected local resource in the operating
     * system's native file manager.
     */
    override fun actionPerformed(
        e: AnActionEvent,
    ) {

        val path =
            ResourceActionSupport.target(e)?.resolvedPath
                ?: return

        RevealFileAction.openFile(
            File(path)
        )
    }
}