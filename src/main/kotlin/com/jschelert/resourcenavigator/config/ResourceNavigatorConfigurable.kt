package com.jschelert.resourcenavigator.config

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import com.jschelert.resourcenavigator.util.ResourceExtensionRegistry
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * =================================================================================================
 * ResourceNavigatorConfigurable
 * =================================================================================================
 *
 * IDE Configuration
 * -----------------
 * Implements the Resource Navigator settings page within the IntelliJ Platform
 * Settings dialog.
 *
 * Behavior
 * --------
 * • Presents editable Resource Navigator configuration options.
 * • Synchronizes UI state with ResourceNavigatorSettings.
 * • Persists user changes through the PersistentStateComponent.
 * • Normalizes configured resource extensions before saving.
 *
 * Responsibilities
 * ----------------
 * • Construct the Settings UI.
 * • Detect modified configuration values.
 * • Apply user configuration changes.
 * • Restore persisted configuration.
 * • Dispose UI resources when no longer needed.
 *
 * Dependencies
 * ------------
 * • ResourceNavigatorSettings
 * • ResourceExtensionRegistry
 * • IntelliJ Configurable API
 * • FormBuilder
 *
 * See Also
 * --------
 * • ResourceNavigatorSettings
 * • ResourceExtensionRegistry
 */
class ResourceNavigatorConfigurable : Configurable {

    //
    // Settings panel.
    //
    private var panel: JPanel? = null

    //
    // Resource extension configuration.
    //
    private val extensionsArea =
        JBTextArea(8, 45)

    //
    // Resource resolution options.
    //
    private val sourceDir =
        JBCheckBox(
            "Resolve relative paths against the current source file directory"
        )

    private val projectRoot =
        JBCheckBox(
            "Resolve relative paths against the project root"
        )

    private val anyExisting =
        JBCheckBox(
            "Treat any existing path-like string as a resource"
        )

    //
    // Navigation options.
    //
    private val urls =
        JBCheckBox(
            "Enable URL navigation"
        )

    //
    // Inspection options.
    //
    private val warnings =
        JBCheckBox(
            "Warn about missing resources"
        )

    /**
     * Return the Settings page display name.
     */
    override fun getDisplayName(): String =
        "Resource Navigator"


    /**
     * Construct the Resource Navigator settings panel.
     *
     * Workflow
     * --------
     * • Restore the current persisted settings.
     * • Construct the IntelliJ Settings UI.
     * • Return the root settings panel.
     */
    override fun createComponent(): JComponent {

        //
        // Initialize the UI from persisted settings.
        //
        reset()

        //
        // Construct the settings panel.
        //
        panel =
            FormBuilder.createFormBuilder()

                .addLabeledComponent(
                    JBLabel("Recognized file extensions (one per line):"),
                    JBScrollPane(extensionsArea),
                    1,
                    false,
                )

                .addComponent(sourceDir)
                .addComponent(projectRoot)
                .addComponent(anyExisting)
                .addComponent(urls)
                .addComponent(warnings)

                .addComponentFillVertically(
                    JPanel(),
                    0,
                )

                .panel

        //
        // Return the root settings panel.
        //
        return panel!!
    }

    /**
     * Determine whether the settings have been modified.
     *
     * Workflow
     * --------
     * • Retrieve the persisted plugin settings.
     * • Compare each UI control against the persisted state.
     * • Return true when any setting has changed.
     */
    override fun isModified(): Boolean {

        //
        // Retrieve the persisted plugin settings.
        //
        val s =
            ResourceNavigatorSettings.getInstance().state

        //
        // Compare the current UI state against the persisted settings.
        //
        return lines(extensionsArea.text) != s.extensions ||
                sourceDir.isSelected != s.resolveAgainstSourceDirectory ||
                projectRoot.isSelected != s.resolveAgainstProjectRoot ||
                anyExisting.isSelected != s.acceptAnyPathLikeValue ||
                urls.isSelected != s.enableUrlNavigation ||
                warnings.isSelected != s.warnOnMissingResources
    }

    /**
     * Apply the current UI settings to the persisted plugin state.
     *
     * Workflow
     * --------
     * • Retrieve the persisted plugin settings.
     * • Copy the current UI values into the persisted state.
     */
    override fun apply() {

        //
        // Retrieve the persisted plugin settings.
        //
        val s =
            ResourceNavigatorSettings.getInstance().state

        //
        // Persist the current UI settings.
        //
        s.extensions =
            lines(extensionsArea.text).toMutableList()

        s.resolveAgainstSourceDirectory =
            sourceDir.isSelected

        s.resolveAgainstProjectRoot =
            projectRoot.isSelected

        s.acceptAnyPathLikeValue =
            anyExisting.isSelected

        s.enableUrlNavigation =
            urls.isSelected

        s.warnOnMissingResources =
            warnings.isSelected
    }

    /**
     * Restore the UI from the persisted plugin settings.
     *
     * Workflow
     * --------
     * • Retrieve the persisted plugin settings.
     * • Update each UI control from the persisted state.
     */
    override fun reset() {

        //
        // Retrieve the persisted plugin settings.
        //
        val s =
            ResourceNavigatorSettings.getInstance().state

        //
        // Restore the UI from the persisted settings.
        //
        extensionsArea.text =
            s.extensions.joinToString("\n")

        sourceDir.isSelected =
            s.resolveAgainstSourceDirectory

        projectRoot.isSelected =
            s.resolveAgainstProjectRoot

        anyExisting.isSelected =
            s.acceptAnyPathLikeValue

        urls.isSelected =
            s.enableUrlNavigation

        warnings.isSelected =
            s.warnOnMissingResources
    }

    /**
     * Release UI resources associated with the settings panel.
     */
    override fun disposeUIResources() {

        //
        // Release the settings panel.
        //
        panel = null
    }

    /**
     * Normalize and deduplicate user-entered resource extensions.
     *
     * Workflow
     * --------
     * • Split the input into individual lines.
     * • Normalize each resource extension.
     * • Remove empty entries.
     * • Remove duplicate extensions.
     * • Return the normalized extension list.
     */
    private fun lines(
        value: String,
    ): List<String> =

        //
        // Normalize and deduplicate the extension list.
        //
        value.lines()
            .map(ResourceExtensionRegistry::normalize)
            .filter(String::isNotEmpty)
            .distinct()
}