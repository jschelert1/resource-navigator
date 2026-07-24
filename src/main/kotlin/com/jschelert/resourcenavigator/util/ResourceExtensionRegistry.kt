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
 *
 * Revision History
 * ----------------
 * v2.0.0 — 2026-07-23 (JS)
 * • Centralized extension-based resource navigation policy in
 *   ResourceExtensionRegistry.
 * • Added browser and external-application extension mappings with
 *   user-visible navigation labels.
 * • Added openMode() to select browser, external, or IDE navigation from
 *   normalized filename extensions.
 * • Added externalLabel() for extension-specific external application labels.
 * • Centralized the default recognized resource-extension list across images,
 *   documents, source files, and data formats.
 * • Added extension normalization for case-insensitive comparisons and
 *   optional leading-period handling.
 */
object ResourceExtensionRegistry {

    /*
     * --------------------------------------------------
     * Extension identifiers
     * --------------------------------------------------
     */

    // Images

    private const val EXT_SVG = "svg"
    private const val EXT_PNG = "png"
    private const val EXT_JPG = "jpg"
    private const val EXT_JPEG = "jpeg"
    private const val EXT_GIF = "gif"
    private const val EXT_WEBP = "webp"
    private const val EXT_BMP = "bmp"
    private const val EXT_ICO = "ico"

    // Documents

    private const val EXT_MD = "md"
    private const val EXT_MARKDOWN = "markdown"

    // Browser documents

    private const val EXT_HTML = "html"
    private const val EXT_HTM = "htm"
    private const val EXT_MHTML = "mhtml"

    // External documents

    private const val EXT_PDF = "pdf"

    private const val EXT_DOC = "doc"
    private const val EXT_DOCX = "docx"

    private const val EXT_XLS = "xls"
    private const val EXT_XLSX = "xlsx"
    private const val EXT_XLSM = "xlsm"

    private const val EXT_PPT = "ppt"
    private const val EXT_PPTX = "pptx"

    private const val EXT_ODT = "odt"
    private const val EXT_ODS = "ods"
    private const val EXT_ODP = "odp"

    private const val EXT_VSDX = "vsdx"
    private const val EXT_DRAWIO = "drawio"

    // Source

    private const val EXT_PY = "py"
    private const val EXT_PYW = "pyw"
    private const val EXT_IPYNB = "ipynb"

    // Data

    private const val EXT_TXT = "txt"
    private const val EXT_CSV = "csv"
    private const val EXT_TSV = "tsv"
    private const val EXT_JSON = "json"
    private const val EXT_YAML = "yaml"
    private const val EXT_YML = "yml"
    private const val EXT_XML = "xml"
    private const val EXT_TOML = "toml"

    /*
     * --------------------------------------------------
     * Navigation labels
     * --------------------------------------------------
     */

    private const val LABEL_BROWSER = "Open in Browser"
    private const val LABEL_EXTERNAL = "Open Externally"
    private const val LABEL_WORD = "Open in Word"
    private const val LABEL_EXCEL = "Open in Excel"
    private const val LABEL_POWERPOINT = "Open in PowerPoint"

    // --------------------------------------------------
    // Browser navigation
    // --------------------------------------------------

    /**
     * Map browser-oriented resource extensions to their navigation label.
     */
    private val browserLabels =
        linkedMapOf(
            EXT_HTML to LABEL_BROWSER,
            EXT_HTM to LABEL_BROWSER,
            EXT_MHTML to LABEL_BROWSER,
        )

    // --------------------------------------------------
    // IDE navigation
    // --------------------------------------------------

    /**
     * Define resource extensions that should open directly within the IDE.
     *
     * These formats are primarily source, text, structured-data, and
     * configuration resources for which IDE editing and navigation are
     * appropriate.
     */
    private val ideExtensions =
        setOf(
            EXT_PY,
            EXT_PYW,
            EXT_IPYNB,

            EXT_MD,
            EXT_MARKDOWN,

            EXT_TXT,
            EXT_CSV,
            EXT_TSV,
            EXT_JSON,
            EXT_YAML,
            EXT_YML,
            EXT_XML,
            EXT_TOML,
        )

    // --------------------------------------------------
    // External application navigation
    // --------------------------------------------------

    /**
     * Map externally opened resource extensions to their preferred
     * application-specific navigation label.
     */
    private val externalLabels =
        linkedMapOf(
            EXT_PDF to LABEL_EXTERNAL,

            EXT_DOC to LABEL_WORD,
            EXT_DOCX to LABEL_WORD,

            EXT_XLS to LABEL_EXCEL,
            EXT_XLSX to LABEL_EXCEL,
            EXT_XLSM to LABEL_EXCEL,

            EXT_PPT to LABEL_POWERPOINT,
            EXT_PPTX to LABEL_POWERPOINT,

            EXT_ODT to LABEL_EXTERNAL,
            EXT_ODS to LABEL_EXTERNAL,
            EXT_ODP to LABEL_EXTERNAL,

            EXT_VSDX to LABEL_EXTERNAL,
            EXT_DRAWIO to LABEL_EXTERNAL,
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

    /**
     * Return the preferred navigation mode for the supplied extension.
     *
     * Navigation Mode Selection
     * -------------------------
     *
     * +-------------+----------------------+---------------------------+
     * | Extension   | Extension Group      | Navigation Mode           |
     * +-------------+----------------------+---------------------------+
     * | html        | Browser              | ResourceOpenMode.BROWSER  |
     * | mhtml       | Browser              | ResourceOpenMode.BROWSER  |
     * | py          | IDE                  | ResourceOpenMode.IDE      |
     * | txt         | IDE                  | ResourceOpenMode.IDE      |
     * | json        | IDE                  | ResourceOpenMode.IDE      |
     * | pdf         | External             | ResourceOpenMode.EXTERNAL |
     * | docx        | External             | ResourceOpenMode.EXTERNAL |
     * | xlsx        | External             | ResourceOpenMode.EXTERNAL |
     * | unknown     | Default              | ResourceOpenMode.EXTERNAL |
     * +-------------+----------------------+---------------------------+
     *
     * The supplied extension is normalized before lookup, allowing equivalent
     * forms such as "PDF", ".pdf", and " pdf " to select the same navigation
     * mode.
     *
     * Extensions explicitly registered for browser, IDE, or external navigation
     * use their corresponding modes. Unregistered extensions default to external
     * navigation so that unknown resource types are opened by the operating
     * system rather than assumed to be IDE-editable.
     */
    fun openMode(
        extension: String,
    ): ResourceOpenMode {

        //
        // Normalize the extension before registry lookup.
        //
        val normalized =
            normalize(extension)

        //
        // Select the navigation mode from the registered extension groups.
        //
        return when (normalized) {

            //
            // Open browser-oriented resources using browser navigation.
            //
            in browserExtensions ->
                ResourceOpenMode.BROWSER

            //
            // Open IDE-oriented resources directly within the IDE.
            //
            in ideExtensions ->
                ResourceOpenMode.IDE

            //
            // Open externally registered resources using their system application.
            //
            in externalExtensions ->
                ResourceOpenMode.EXTERNAL

            //
            // Default unregistered resource types to their system application.
            //
            else ->
                ResourceOpenMode.EXTERNAL
        }
    }

    /**
     * Return the preferred external-application navigation label for the
     * supplied extension.
     *
     * External Navigation Labels
     * --------------------------
     *
     * +-------------+----------------------+----------------------+
     * | Extension   | Application Group    | Label                |
     * +-------------+----------------------+----------------------+
     * | pdf         | Generic external     | Open Externally      |
     * | docx        | Microsoft Word       | Open in Word         |
     * | xlsx        | Microsoft Excel      | Open in Excel        |
     * | pptx        | Microsoft PowerPoint | Open in PowerPoint   |
     * | odt         | Generic external     | Open Externally      |
     * | drawio      | Generic external     | Open Externally      |
     * | unknown     | Unregistered         | Open Externally      |
     * +-------------+----------------------+----------------------+
     *
     * The supplied extension is normalized before lookup, allowing equivalent
     * forms such as "DOCX", ".docx", and " docx " to return the same label.
     *
     * Extensions without an application-specific mapping fall back to the
     * generic external-navigation label.
     */
    fun externalLabel(
        extension: String,
    ): String =

        //
        // Return the registered label or the generic external fallback.
        //
        externalLabels[normalize(extension)]
            ?: LABEL_EXTERNAL

    // --------------------------------------------------
    // Default recognized extensions
    // --------------------------------------------------

    /**
     * Return the default list of resource extensions recognized by Resource
     * Navigator.
     *
     * Default Extension Groups
     * ------------------------
     *
     * +----------------------+--------------------------------------------------+
     * | Group                | Extensions                                       |
     * +----------------------+--------------------------------------------------+
     * | Images               | svg, png, jpg, jpeg, gif, webp, bmp, ico         |
     * | Documents            | md, markdown                                     |
     * | Browser              | html, htm, mhtml                                 |
     * | External             | pdf, doc, docx, xls, xlsx, xlsm, ppt, pptx, ... |
     * | Source               | py, pyw, ipynb                                   |
     * | Data                 | txt, csv, tsv, json, yaml, yml, xml, toml        |
     * +----------------------+--------------------------------------------------+
     *
     * Browser and external extensions are derived directly from their navigation
     * registries so that navigation policy and default recognition remain
     * synchronized.
     *
     * The resulting list is de-duplicated before being returned.
     */
    fun defaultExtensions(): List<String> =
        buildList {

            /*
             * --------------------------------------------------
             * Images
             * --------------------------------------------------
             */

            //
            // Add image formats recognized as local resources.
            //
            addAll(
                listOf(
                    EXT_SVG,
                    EXT_PNG,
                    EXT_JPG,
                    EXT_JPEG,
                    EXT_GIF,
                    EXT_WEBP,
                    EXT_BMP,
                    EXT_ICO,
                )
            )

            /*
             * --------------------------------------------------
             * Documents
             * --------------------------------------------------
             */

            //
            // Add document formats handled directly by the IDE.
            //
            addAll(
                listOf(
                    EXT_MD,
                    EXT_MARKDOWN,
                )
            )

            //
            // Include all extensions assigned to browser navigation.
            //
            addAll(browserExtensions)

            //
            // Include all extensions assigned to external applications.
            //
            addAll(externalExtensions)

            /*
             * --------------------------------------------------
             * Source
             * --------------------------------------------------
             */

            //
            // Add Python source and notebook formats.
            //
            addAll(
                listOf(
                    EXT_PY,
                    EXT_PYW,
                    EXT_IPYNB,
                )
            )

            /*
             * --------------------------------------------------
             * Data
             * --------------------------------------------------
             */

            //
            // Add common text, structured-data, and configuration formats.
            //
            addAll(
                listOf(
                    EXT_TXT,
                    EXT_CSV,
                    EXT_TSV,
                    EXT_JSON,
                    EXT_YAML,
                    EXT_YML,
                    EXT_XML,
                    EXT_TOML,
                )
            )

        }

            //
            // Ensure each extension appears only once in the default registry.
            //
            .distinct()

    // --------------------------------------------------
    // Utilities
    // --------------------------------------------------

    /**
     * Normalize a filename extension for consistent case-insensitive registry
     * lookup and comparison.
     *
     * Extension Normalization
     * -----------------------
     *
     * +----------------------+----------------------+
     * | Input                | Normalized Result    |
     * +----------------------+----------------------+
     * | "pdf"                | pdf                  |
     * | "PDF"                | pdf                  |
     * | ".pdf"               | pdf                  |
     * | ".PDF"               | pdf                  |
     * | " pdf "              | pdf                  |
     * | " .DOCX "            | docx                 |
     * | ""                   | ""                   |
     * +----------------------+----------------------+
     *
     * Behavior
     * --------
     * • Removes leading and trailing whitespace.
     * • Removes any leading period from the extension.
     * • Converts the extension to lowercase.
     *
     * This allows callers to supply common extension representations without
     * first normalizing them themselves.
     */
    fun normalize(
        extension: String,
    ): String =

        //
        // Remove surrounding whitespace and leading periods, then normalize case.
        //
        extension
            .trim()
            .trimStart('.')
            .lowercase()
}