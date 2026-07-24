package com.jschelert.resourcenavigator.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jschelert.resourcenavigator.util.ResourceExtensionRegistry

/**
 * =================================================================================================
 * ResourceNavigatorSettings
 * =================================================================================================
 *
 * Persistent Settings
 * -------------------
 * Stores the persistent user configuration for the Resource Navigator plugin.
 *
 * Behavior
 * --------
 * • Persists user configuration using the IntelliJ PersistentStateComponent API.
 * • Provides application-wide access to Resource Navigator settings.
 * • Normalizes configured resource extensions before runtime use.
 * • Initializes the default extension list from ResourceExtensionRegistry.
 *
 * Responsibilities
 * ----------------
 * • Persist Resource Navigator configuration.
 * • Provide normalized runtime configuration.
 * • Supply application-wide singleton access.
 * • Initialize default configuration values.
 *
 * Dependencies
 * ------------
 * • PersistentStateComponent
 * • XmlSerializerUtil
 * • ApplicationManager
 * • ResourceExtensionRegistry
 *
 * See Also
 * --------
 * • ResourceNavigatorConfigurable
 * • ResourceExtensionRegistry
 */
@State(
    name = "ResourceNavigatorSettings",
    storages = [
        Storage("resourceNavigator.xml")
    ],
)
class ResourceNavigatorSettings :
    PersistentStateComponent<ResourceNavigatorSettings.State> {

    /**
     * Persistent configuration state.
     */
    data class State(

        var extensions: MutableList<String> =
            defaultExtensions().toMutableList(),

        var resolveAgainstSourceDirectory: Boolean = true,

        var resolveAgainstProjectRoot: Boolean = true,

        var acceptAnyPathLikeValue: Boolean = false,

        var enableUrlNavigation: Boolean = true,

        var warnOnMissingResources: Boolean = true,
    )

    private var state =
        State()

    override fun getState(): State =
        state

    override fun loadState(
        state: State,
    ) =
        XmlSerializerUtil.copyBean(
            state,
            this.state,
        )

    /**
     * Return the normalized configured resource extensions.
     */
    fun extensions(): Set<String> =
        state.extensions
            .map(ResourceExtensionRegistry::normalize)
            .filter(String::isNotEmpty)
            .toSet()

    companion object {

        /**
         * Enable Resource Navigator development diagnostics.
         */
        const val DIAGNOSTICS_ENABLED: Boolean = false

        /**
         * Return the application-wide Resource Navigator settings instance.
         */
        fun getInstance(): ResourceNavigatorSettings =
            ApplicationManager
                .getApplication()
                .getService(ResourceNavigatorSettings::class.java)

        /**
         * Return the default recognized resource extensions.
         */
        fun defaultExtensions(): List<String> =
            ResourceExtensionRegistry.defaultExtensions()
    }
}