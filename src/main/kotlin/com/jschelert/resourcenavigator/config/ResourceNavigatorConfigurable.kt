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

    private var panel: JPanel? = null

    private val extensionsArea =
        JBTextArea(8, 45)

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

    private val urls =
        JBCheckBox(
            "Enable URL navigation"
        )

    private val warnings =
        JBCheckBox(
            "Warn about missing resources"
        )

    override fun getDisplayName(): String =
        "Resource Navigator"

    override fun createComponent(): JComponent {

        reset()

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

        return panel!!
    }

    override fun isModified(): Boolean {

        val s =
            ResourceNavigatorSettings.getInstance().state

        return lines(extensionsArea.text) != s.extensions ||
                sourceDir.isSelected != s.resolveAgainstSourceDirectory ||
                projectRoot.isSelected != s.resolveAgainstProjectRoot ||
                anyExisting.isSelected != s.acceptAnyExistingPath ||
                urls.isSelected != s.enableUrlNavigation ||
                warnings.isSelected != s.warnOnMissingResources
    }

    override fun apply() {

        val s =
            ResourceNavigatorSettings.getInstance().state

        s.extensions =
            lines(extensionsArea.text).toMutableList()

        s.resolveAgainstSourceDirectory =
            sourceDir.isSelected

        s.resolveAgainstProjectRoot =
            projectRoot.isSelected

        s.acceptAnyExistingPath =
            anyExisting.isSelected

        s.enableUrlNavigation =
            urls.isSelected

        s.warnOnMissingResources =
            warnings.isSelected
    }

    override fun reset() {

        val s =
            ResourceNavigatorSettings.getInstance().state

        extensionsArea.text =
            s.extensions.joinToString("\n")

        sourceDir.isSelected =
            s.resolveAgainstSourceDirectory

        projectRoot.isSelected =
            s.resolveAgainstProjectRoot

        anyExisting.isSelected =
            s.acceptAnyExistingPath

        urls.isSelected =
            s.enableUrlNavigation

        warnings.isSelected =
            s.warnOnMissingResources
    }

    override fun disposeUIResources() {
        panel = null
    }

    /**
     * Normalize and deduplicate user-entered resource extensions.
     */
    private fun lines(
        value: String,
    ): List<String> =
        value.lines()
            .map(ResourceExtensionRegistry::normalize)
            .filter(String::isNotEmpty)
            .distinct()
}