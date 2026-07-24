package com.jschelert.resourcenavigator.navigation

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jschelert.resourcenavigator.util.ResourceExtensionRegistry
import com.jschelert.resourcenavigator.util.ResourceKind
import com.jschelert.resourcenavigator.util.ResourceTarget
import java.awt.Desktop

/**
 * =================================================================================================
 * ResourceDispatcher
 * =================================================================================================
 *
 * Resource Dispatcher
 * -------------------
 * Centralizes resource-opening behavior for Resource Navigator. Given a
 * resolved ResourceTarget and an optional navigation mode, this class chooses
 * the appropriate mechanism for opening the resource.
 *
 * Behavior
 * --------
 * • Opens HTTP/HTTPS URLs in the system browser.
 * • Opens browser-oriented local files in the browser.
 * • Opens externally-associated document types using the operating system.
 * • Opens all remaining resources inside the IDE editor.
 * • Supports explicit IDE, Browser, and External navigation modes selected
 *   from the Choose Declaration dialog.
 * • Selects local directories in the IDE Project view.
 *
 * Responsibilities
 * ----------------
 * • Dispatch navigation requests.
 * • Execute the requested open mode.
 * • Isolate platform-specific launch behavior.
 * • Keep navigation execution independent of resource classification.
 *
 * Dependencies
 * ------------
 * • ResourceExtensionRegistry
 * • ResourceTarget
 * • ResourceKind
 * • ResourceOpenMode
 * • BrowserUtil
 * • FileEditorManager
 * • Desktop
 *
 * See Also
 * --------
 * • ResourceResolver
 * • ResourceNavigationTargetFactory
 * • ResourceReferenceLocal
 * • ResourceReferenceUrl
 *
 * Architectural Notes
 * -------------------
 * • ResourceDispatcher assumes the supplied ResourceTarget has already been
 *   validated and resolved.
 *
 * • Extension-based navigation policy is defined entirely by
 *   ResourceExtensionRegistry. This class simply executes the resulting
 *   ResourceOpenMode.
 */
object ResourceDispatcher {

    /**
     * Open a resource using the default Resource Navigator policy.
     */
    fun open(
        project: Project?,
        target: ResourceTarget,
    ) {

        when (target.kind) {

            ResourceKind.URL ->
                openUrl(target.sourceValue)

            ResourceKind.LOCAL_FILE ->
                openDefault(project, target)
        }
    }

    /**
     * Open a resource using an explicit navigation mode selected from the
     * Choose Declaration dialog.
     */
    fun open(
        project: Project?,
        target: ResourceTarget,
        mode: ResourceOpenMode,
    ) {

        when (mode) {

            ResourceOpenMode.IDE ->
                openInIde(project, target.virtualFile)

            ResourceOpenMode.EXTERNAL ->
                openExternally(target.virtualFile)

            ResourceOpenMode.BROWSER ->
                openInBrowser(target)
        }
    }

    /**
     * Open a local resource using the default extension policy defined by
     * ResourceExtensionRegistry.
     */
    private fun openDefault(
        project: Project?,
        target: ResourceTarget,
    ) {

        val file =
            target.virtualFile
                ?: return

        val mode =
            ResourceExtensionRegistry.openMode(
                file.extension.orEmpty(),
            )

        when (mode) {

            ResourceOpenMode.IDE ->
                openInIde(project, file)

            ResourceOpenMode.EXTERNAL ->
                openExternally(file)

            ResourceOpenMode.BROWSER ->
                openInBrowser(target)
        }
    }

    /**
     * Open a URL using the system browser.
     */
    private fun openUrl(
        url: String,
    ) {
        BrowserUtil.browse(url)
    }

    /**
     * Open a resource in the system browser.
     */
    private fun openInBrowser(
        target: ResourceTarget,
    ) {

        when (target.kind) {

            ResourceKind.URL ->
                BrowserUtil.browse(target.sourceValue)

            ResourceKind.LOCAL_FILE ->
                target.virtualFile?.let { file ->

                    BrowserUtil.browse(
                        file.toNioPath().toUri()
                    )
                }
        }
    }

    /**
     * Open a local resource using IDE-oriented navigation.
     *
     * Files are opened in the IDE editor, while directories are opened using
     * the operating system's file manager.
     */
    private fun openInIde(
        project: Project?,
        file: VirtualFile?,
    ) {

        project ?: return
        file ?: return

        //
        // Select directories within the IDE project view.
        //
        if (file.isDirectory) {

            openExternally(file)

            return
        }

        //
        // Open regular files in the IDE editor.
        //
        FileEditorManager
            .getInstance(project)
            .openFile(file, true)
    }

    /**
     * Open a local resource using the operating system's default application.
     *
     * External launching is delayed briefly so modifier keys used to invoke
     * navigation, such as Ctrl+Click, can be released before the target
     * application starts.
     */
    private fun openExternally(
        file: VirtualFile?,
    ) {

        file ?: return

        if (!Desktop.isDesktopSupported())
            return

        //
        // Delay external launch briefly so navigation modifier keys can be
        // released before the target application starts.
        //
        ApplicationManager
            .getApplication()
            .executeOnPooledThread {

                Thread.sleep(150)

                Desktop
                    .getDesktop()
                    .open(file.toNioPath().toFile())
            }
    }
}