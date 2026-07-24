package com.jschelert.resourcenavigator.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.jschelert.resourcenavigator.navigation.ResourceDispatcher
import com.jschelert.resourcenavigator.util.ResourceKind

/**
 * =================================================================================================
 * OpenResourceAction
 * =================================================================================================
 *
 * IDE Action
 * ----------
 * Opens the resource referenced by the currently selected Python string literal.
 *
 * Behavior
 * --------
 * • Local resources are opened within the IDE when possible.
 * • URL resources are opened using the configured browser navigation.
 * • Other supported resource types are delegated to the appropriate handler.
 * • The action is only enabled when a valid ResourceTarget exists.
 *
 * Responsibilities
 * ----------------
 * • Obtain the current ResourceTarget.
 * • Validate that the target can be opened.
 * • Delegate resource opening to ResourceDispatcher.
 *
 * Dependencies
 * ------------
 * • ResourceActionSupport
 * • ResourceDispatcher
 * • ResourceKind
 *
 * See Also
 * --------
 * • CopyFullPathAction
 * • RevealInFileManagerAction
 * • ResourceDispatcher
 */
class OpenResourceAction : AnAction(), DumbAware {

    /**
     * Perform action updates on IntelliJ's background thread.
     */
    override fun getActionUpdateThread(): ActionUpdateThread =
        ActionUpdateThread.BGT

    /**
     * Enable this action when the current resource can be opened.
     */
    override fun update(e: AnActionEvent) {

        val target =
            ResourceActionSupport.target(e)

        e.presentation.isEnabledAndVisible =
            target?.let {
                it.kind == ResourceKind.URL || it.virtualFile != null
            } == true
    }

    /**
     * Open the currently selected resource.
     */
    override fun actionPerformed(e: AnActionEvent) {

        val target =
            ResourceActionSupport.target(e)
                ?: return

        ResourceDispatcher.open(e.project, target)
    }
}