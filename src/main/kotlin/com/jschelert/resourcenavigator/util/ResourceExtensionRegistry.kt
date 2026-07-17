package com.jschelert.resourcenavigator.util

import com.jschelert.resourcenavigator.navigation.ResourceOpenMode

/**
 * =================================================================================================
 * ResourceExtensionRegistry
 * =================================================================================================
 *
 * Resource Extension Registry
 * ---------------------------
 * Central registry describing resource filename extensions and their preferred
 * navigation behavior throughout Resource Navigator.
 *
 * Behavior
 * --------
 * • Defines browser-supported resource extensions.
 * • Defines externally-opened document extensions.
 * • Supplies default recognized resource extensions.
 * • Provides extension normalization.
 * • Supplies preferred navigation modes.
 * • Supplies user-visible navigation labels.
 *
 * Responsibilities
 * ----------------
 * • Centralize extension metadata.
 * • Eliminate duplicated extension lists.
 * • Provide consistent navigation policy.
 * • Normalize extension comparisons.
 *
 * Dependencies
 * ------------
 * • ResourceOpenMode
 *
 * See Also
 * --------
 * • ResourceNavigationTargetFactory
 * • ResourceDispatcher
 * • ResourceNavigatorSettings
 * • ResourceClassifier
 *
 * Architectural Notes
 * -------------------
 * • This registry intentionally contains no PSI or filesystem logic.
 *
 * • It acts as the authoritative source for extension-based navigation policy
 *   used throughout Resource Navigator.
 *
 * • New resource types should be added here rather than duplicated in
 *   individual navigation classes.
 */
object ResourceExtensionRegistry {

    // --------------------------------------------------
    // Browser navigation
    // --------------------------------------------------

    private val browserLabels = linkedMapOf(

        "html" to "Open in Browser",
        "htm" to "Open in Browser",
        "mhtml" to "Open in Browser",
    )

    // --------------------------------------------------
    // External application navigation
    // --------------------------------------------------

    private val externalLabels = linkedMapOf(

        "pdf" to "Open Externally",

        "doc" to "Open in Word",
        "docx" to "Open in Word",

        "xls" to "Open in Excel",
        "xlsx" to "Open in Excel",
        "xlsm" to "Open in Excel",

        "ppt" to "Open in PowerPoint",
        "pptx" to "Open in PowerPoint",

        "odt" to "Open Externally",
        "ods" to "Open Externally",
        "odp" to "Open Externally",

        "vsdx" to "Open Externally",
        "drawio" to "Open Externally",
    )

    // --------------------------------------------------
    // Cached extension sets
    // --------------------------------------------------

    private val browserExtensions =
        browserLabels.keys

    private val externalExtensions =
        externalLabels.keys

    // --------------------------------------------------
    // Queries
    // --------------------------------------------------

//    /**
//     * Return true if the supplied extension should open in the browser.
//     */
//    fun isBrowserExtension(
//        extension: String,
//    ): Boolean =
//        normalize(extension) in browserExtensions
//
//    /**
//     * Return true if the supplied extension should open using the operating
//     * system's default application.
//     */
//    fun isExternalExtension(
//        extension: String,
//    ): Boolean =
//        normalize(extension) in externalExtensions

    /**
     * Return the preferred navigation mode for the supplied extension.
     */
    fun openMode(
        extension: String,
    ): ResourceOpenMode {

        val normalized =
            normalize(extension)

        return when (normalized) {

            in browserExtensions ->
                ResourceOpenMode.BROWSER

            in externalExtensions ->
                ResourceOpenMode.EXTERNAL

            else ->
                ResourceOpenMode.IDE
        }
    }

//    /**
//     * Return the preferred browser label.
//     */
//    fun browserLabel(
//        extension: String,
//    ): String =
//        browserLabels[normalize(extension)]
//            ?: "Open in Browser"

    /**
     * Return the preferred external-application label.
     */
    fun externalLabel(
        extension: String,
    ): String =
        externalLabels[normalize(extension)]
            ?: "Open Externally"

    // --------------------------------------------------
    // Default recognized extensions
    // --------------------------------------------------

    /**
     * Return the default list of recognized resource extensions.
     */
    fun defaultExtensions(): List<String> =
        buildList {

            // --------------------------------------------------
            // Images
            // --------------------------------------------------

            addAll(
                listOf(
                    "svg", "png", "jpg", "jpeg",
                    "gif", "webp", "bmp", "ico",
                )
            )

            // --------------------------------------------------
            // Documents
            // --------------------------------------------------

            addAll(
                listOf(
                    "md", "markdown",
                )
            )

            addAll(browserExtensions)
            addAll(externalExtensions)

            // --------------------------------------------------
            // Source
            // --------------------------------------------------

            addAll(
                listOf(
                    "py", "pyw", "ipynb",
                )
            )

            // --------------------------------------------------
            // Data
            // --------------------------------------------------

            addAll(
                listOf(
                    "txt", "csv", "tsv",
                    "json", "yaml", "yml",
                    "xml", "toml",
                )
            )

        }.distinct()

    // --------------------------------------------------
    // Utilities
    // --------------------------------------------------

    /**
     * Normalize a filename extension for case-insensitive comparison.
     */
    fun normalize(
        extension: String,
    ): String =
        extension
            .trim()
            .trimStart('.')
            .lowercase()
}