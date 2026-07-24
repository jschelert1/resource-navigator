package com.jschelert.resourcenavigator.navigation

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.config.ResourceNavigatorSettings

import com.jschelert.resourcenavigator.util.PythonStringResolver
import com.jschelert.resourcenavigator.util.PythonStringUtil
import com.jschelert.resourcenavigator.util.ResourceClassifier
import com.jschelert.resourcenavigator.util.ResourceResolver
import com.jschelert.resourcenavigator.util.PythonResourceContext

/**
 * =================================================================================================
 * ResourceReferenceContributor
 * =================================================================================================
 *
 * PSI Reference Contributor
 * -------------------------
 * Registers Resource Navigator PSI reference providers for Python string
 * literals and constructs references for resources recognized by the plugin.
 *
 * Behavior
 * --------
 * • Registers a PSI reference provider for Python string literals.
 * • Gives precedence to bracketed resource citations.
 * • Preserves ordinary one-resource-per-string navigation.
 * • Resolves ordinary Python strings through PythonStringResolver.
 * • Filters candidate resources using ResourceClassifier.
 * • Creates ResourceReferenceLocal or ResourceReferenceUrl references.
 * • Suppresses references to missing local filesystem resources.
 *
 * Responsibilities
 * ----------------
 * • Register IntelliJ PSI reference providers.
 * • Dispatch between citation and ordinary resource handling.
 * • Evaluate ordinary Python string literals into resolved string values.
 * • Coordinate resource classification and resolution.
 * • Construct PSI references for recognized resources.
 *
 * Dependencies
 * ------------
 * • PythonStringResolver
 * • PythonStringUtil
 * • ResourceCitationParser
 * • ResourceClassifier
 * • ResourceResolver
 * • ResourceReferenceLocal
 * • ResourceReferenceUrl
 * • IntelliJ PSI Reference APIs
 *
 * Data Flow
 * ---------
 *
 * Ordinary resource handling follows two related but distinct flows.
 *
 * The first flow determines the semantic value of the Python string and uses
 * that value for resource classification and resolution:
 *
 *     PyStringLiteralExpression
 *              |
 *              v
 *     PythonStringResolver
 *              |
 *              v
 *     PythonResolvedString.resolvedString
 *              |
 *              +----> ResourceClassifier
 *              |
 *              +----> ResourceResolver
 *              |
 *              v
 *     ResourceReferenceLocal / ResourceReferenceUrl
 *
 * PythonStringResolver evaluates the Python literal into its reconstructed
 * compile-time string value. ResourceClassifier determines whether that value
 * represents a supported resource, while ResourceResolver maps supported local
 * values to concrete filesystem targets. The resulting resource type determines
 * which PSI reference implementation is created.
 *
 * The second flow preserves the original PSI structure needed by IntelliJ to
 * determine the editor range associated with the reference:
 *
 *     PyStringLiteralExpression
 *              |
 *              v
 *     PythonStringUtil.contentRange()
 *              |
 *              v
 *     IntelliJ PSI reference range
 *
 * This separation is intentional. The reconstructed string value describes
 * what the Python expression means, while the PyStringLiteralExpression retains
 * the source structure and text ranges required by IntelliJ for highlighting,
 * Ctrl+Click, and PSI reference presentation.
 *
 * Architectural Notes
 * -------------------
 * • Bracketed citations intentionally take precedence over ordinary resource
 *   strings, allowing multiple independently navigable resources to exist
 *   within a single Python string literal.
 *
 * • Ordinary Python strings are evaluated through PythonStringResolver before
 *   classification and resource resolution. Navigation therefore operates on
 *   the reconstructed compile-time string value rather than directly on the
 *   raw Python source text.
 *
 * • PythonStringUtil remains responsible for source-oriented operations such
 *   as determining the content range used to construct PSI references.
 *
 * • Semantic string resolution and PSI source-range handling are deliberately
 *   kept separate. This distinction is particularly important for adjacent and
 *   multiline Python string literals, where the reconstructed value and physical
 *   source representation may differ.
 *
 * • ResourceReferenceContributor serves as the central dispatcher between the
 *   IntelliJ PSI infrastructure and the Resource Navigator navigation subsystem.
 *
 * See Also
 * --------
 * • PythonStringResolver
 * • PythonStringUtil
 * • ResourceCitationParser
 * • ResourceClassifier
 * • ResourceResolver
 * • ResourceReferenceLocal
 * • ResourceReferenceUrl
 * • ResourceGotoDeclarationHandler
 *
 * Revision History
 * ----------------
 * v2.0.0 — 2026-07-23 (JS)
 * • Refactored ordinary resource handling to evaluate Python string literals
 *   through PythonStringResolver before classification and resolution.
 * • Introduced the PythonResolvedString pipeline to separate source-oriented
 *   PSI handling from reconstructed compile-time string values.
 * • Updated resource classification and resolution to operate on the resolved
 *   string value while preserving PythonStringUtil for PSI reference ranges.
 * • Documented the separate semantic-resolution and PSI-range data flows.
 * • Prepared PSI reference generation for expanded constant-string evaluation
 *   and Tier 3/4 resource navigation.
 */

class ResourceReferenceContributor : PsiReferenceContributor() {

    /**
     * Register the PSI reference provider for Python string literals.
     *
     * Reference Behavior
     * ------------------
     *
     * +------------------------------------------+--------------------------+---------------------------+
     * | Python String Example                    | Resource Type            | Reference Generated       |
     * +------------------------------------------+--------------------------+---------------------------+
     * | "docs/manual.pdf"                        | Existing local file      | ResourceReferenceLocal    |
     * | "docs/missing.pdf"                       | Missing local file       | None                      |
     * | "https://example.com"                    | URL                      | ResourceReferenceUrl      |
     * | "[docs/manual.pdf]"                      | Bracketed local citation | ResourceReferenceLocal    |
     * | "[https://example.com]"                  | Bracketed URL citation   | ResourceReferenceUrl      |
     * | "\[one.pdf\] \[two.pdf\]"                | Multiple citations       | One reference per citation|
     * | "*.pdf"                                  | Glob pattern             | None                      |
     * | f"{runtime_value}"                       | Unresolved expression    | None                      |
     * +------------------------------------------+--------------------------+---------------------------+
     *
     * Bracketed citations take precedence over ordinary one-resource-per-string
     * handling. When one or more citations are present, each valid citation
     * receives an independent PSI reference.
     */
    override fun registerReferenceProviders(
        registrar: PsiReferenceRegistrar,
    ) {

        //
        // Register the reference provider for Python string literals.
        //
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(
                PyStringLiteralExpression::class.java,
            ),
            object : PsiReferenceProvider() {

                /**
                 * Create resource references for the supplied Python string literal.
                 */
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {

                    //
                    // Recover the Python string literal PSI element.
                    //
                    val literal =
                        element as? PyStringLiteralExpression
                            ?: return PsiReference.EMPTY_ARRAY

                    //
                    // Emit PSI reference-provider diagnostics when global diagnostics are enabled.
                    //
                    if (ResourceNavigatorSettings.DIAGNOSTICS_ENABLED) {
                        diagnoseReferenceProvider(literal)
                    }

                    //
                    // Parse bracketed resource citations from the literal.
                    //
                    val citations =
                        ResourceCitationParser.parse(literal)

                    //
                    // Create independent references for bracketed citations.
                    //
                    if (citations.isNotEmpty()) {
                        return createCitationReferences(
                            literal = literal,
                            citations = citations,
                        )
                    }

                    //
                    // Fall back to ordinary one-resource-per-string handling.
                    //
                    return createOrdinaryReferences(literal)
                }
            },
            PsiReferenceRegistrar.HIGHER_PRIORITY,
        )
    }

    /**
     * Create the PSI reference for an ordinary Python string containing one
     * complete resource.
     *
     * Behavior
     * --------
     * • Resolves the Python string into its reconstructed compile-time value.
     * • Validates the resolved resource using ResourceClassifier.
     * • Creates URL references for HTTP/HTTPS resources.
     * • Resolves local filesystem resources using ResourceResolver.
     * • Creates the appropriate PSI reference from the resolved resource.
     * • Returns an empty array when navigation is not applicable.
     *
     * Reference Behavior
     * ------------------
     *
     * +------------------------------------------+-------------------------+------------------------+
     * | Python String Example                    | Resolved Resource       | Reference Generated    |
     * +------------------------------------------+-------------------------+------------------------+
     * | 'docs/manual.pdf'                        | Existing relative file  | ResourceReferenceLocal |
     * | 'C:\Documents\report.pdf'                | Existing absolute file  | ResourceReferenceLocal |
     * | 'docs/missing.pdf'                       | Missing local file      | None                   |
     * | 'https://example.com'                    | HTTP/HTTPS URL          | ResourceReferenceUrl   |
     * | '*.pdf'                                  | Glob pattern            | None                   |
     * | 'ordinary text'                          | Not a resource          | None                   |
     * | f'{runtime_value}'                       | Unresolved expression   | None                   |
     * +------------------------------------------+-------------------------+------------------------+
     *
     * The Python string is resolved once and the resulting compile-time value
     * is reused for classification and resource resolution, ensuring that all
     * stages operate on the same reconstructed value.
     */
    private fun createOrdinaryReferences(
        literal: PyStringLiteralExpression,
    ): Array<PsiReference> {

        //
        // Determine the supported expression that should be evaluated for this
        // resource while retaining the original literal as the PSI reference source.
        //
        val expression =
            PythonResourceContext.evaluationExpression(literal)

        //
        // Resolve the complete evaluation expression into its compile-time value.
        //
        val resolved =
            PythonStringResolver.resolve(expression)
                ?: return PsiReference.EMPTY_ARRAY

        //
        // Extract and normalize the resolved string value.
        //
        val resolvedValue =
            resolved.resolvedString.trim()

        //
        // Determine whether the resolved value represents a supported resource.
        //
        val shouldHandle =
            ResourceClassifier.shouldHandle(
                literal,
                resolvedValue,
            )

        if (!shouldHandle) {
            return PsiReference.EMPTY_ARRAY
        }

        //
        // Determine the source range used for the IntelliJ PSI reference.
        //
        val range =
            PythonStringUtil.contentRange(literal)

        //
        // Emit ordinary-reference diagnostics when global diagnostics are enabled.
        //
        if (ResourceNavigatorSettings.DIAGNOSTICS_ENABLED) {
            diagnoseOrdinaryReference(
                literal = literal,
                range = range,
                resolvedValue = resolvedValue,
            )
        }

        //
        // Create a URL reference for HTTP/HTTPS resources.
        //
        if (ResourceClassifier.isUrl(resolvedValue)) {
            return arrayOf(
                ResourceReferenceUrl(
                    literal,
                    range,
                )
            )
        }

        //
        // Resolve the ordinary string as a local filesystem resource.
        //
        val target =
            ResourceResolver.resolve(
                literal,
                resolved,
            ) ?: return PsiReference.EMPTY_ARRAY

        //
        // Reject missing local resources.
        //
        if (!target.exists) {
            return PsiReference.EMPTY_ARRAY
        }

        //
        // Create the local filesystem PSI reference.
        //
        return arrayOf(
            ResourceReferenceLocal(
                literal,
                range,
            )
        )
    }

    /**
     * Create one PSI reference for every valid bracketed citation.
     *
     * Behavior
     * --------
     * • Processes every parsed bracketed citation independently.
     * • Supports both URL and local filesystem resources.
     * • Skips empty, invalid, unsupported, or unresolved resources.
     * • Suppresses references to missing local filesystem resources.
     * • Returns one PSI reference per valid citation.
     *
     * Reference Behavior
     * ------------------
     *
     * +------------------------------------------+--------------------------+------------------------+
     * | Python String Example                    | Citation Resource        | Reference Generated    |
     * +------------------------------------------+--------------------------+------------------------+
     * | '[docs/manual.pdf]'                      | Existing relative file   | ResourceReferenceLocal |
     * | '[C:\Documents\report.pdf]'              | Existing absolute file   | ResourceReferenceLocal |
     * | '[docs/missing.pdf]'                     | Missing local file       | None                   |
     * | '[https://example.com]'                  | HTTP/HTTPS URL           | ResourceReferenceUrl   |
     * | '\[one.pdf] \[two.pdf]'                    | Two existing local files | Two local references   |
     * | '\[one.pdf] [https://example.com]'        | Local file + URL         | Two references         |
     * | 'ordinary text'                          | No bracketed citation    | None                   |
     * +------------------------------------------+--------------------------+------------------------+
     *
     * Each citation retains its own source range within the containing Python
     * string, allowing multiple independently navigable resources to coexist
     * within a single literal.
     *
     * Known Limitation
     * ----------------
     * For bracketed resource citations inside Python triple-quoted strings,
     * PyCharm may highlight the entire docstring while Ctrl+Click is held.
     * Navigation is unaffected and opens the correct resource. This appears
     * to be an editor presentation behavior rather than a limitation of
     * resource resolution.
     */
    private fun createCitationReferences(
        literal: PyStringLiteralExpression,
        citations: List<ResourceCitation>,
    ): Array<PsiReference> {

        //
        // Accumulate independently navigable citation references.
        //
        val references =
            mutableListOf<PsiReference>()

        //
        // Process each parsed citation independently.
        //
        citations.forEach { citation ->

            //
            // Normalize the citation value.
            //
            val value =
                citation.text.trim()

            //
            // Ignore empty citations.
            //
            if (value.isEmpty()) {
                return@forEach
            }

            /*
             * Brackets are not required for URLs, but accepting a bracketed
             * URL remains harmless and keeps the citation parser general.
             */
            if (ResourceClassifier.isUrl(value)) {

                //
                // Create a URL reference for this citation.
                //
                references +=
                    ResourceReferenceUrl(
                        literal,
                        citation.range,
                    )

                return@forEach
            }

            //
            // Determine whether the citation represents a supported resource.
            //
            val shouldHandle =
                ResourceClassifier.shouldHandle(
                    literal,
                    value,
                )

            //
            // Ignore unsupported resource values.
            //
            if (!shouldHandle) {
                return@forEach
            }

            //
            // Resolve the citation as a local filesystem resource.
            //
            val target =
                ResourceResolver.resolveLocal(
                    project = literal.project,
                    containingFile = literal.containingFile,
                    sourceValue = value,
                )

            //
            // Ignore missing local resources.
            //
            if (!target.exists) {
                return@forEach
            }

            //
            // Create the local filesystem reference for this citation.
            //
            references +=
                ResourceReferenceLocal(
                    element = literal,
                    range = citation.range,
                    rawValue = citation.text,
                )
        }

        //
        // Return all successfully created citation references.
        //
        return references.toTypedArray()
    }

    /**
     * Print diagnostic information for the Python string literal supplied
     * to the PSI reference provider.
     *
     * Intended for debugging PSI structure, source ranges, and Python string
     * fragment handling during reference creation.
     */
    private fun diagnoseReferenceProvider(
        literal: PyStringLiteralExpression,
    ) {

        println()
        println("===== Reference Provider =====")
        println("literal class = ${literal::class.qualifiedName}")
        println("literal textLength = ${literal.textLength}")
        println("literal text = <${literal.text}>")
        println("literal range = ${literal.textRange}")
        println("decoded fragments = ${literal.stringElements.size}")
    }

    /**
     * Print diagnostic information for an ordinary resource reference.
     *
     * Intended for debugging PSI source ranges and the relationship between
     * the original Python literal and its reconstructed compile-time value.
     */
    private fun diagnoseOrdinaryReference(
        literal: PyStringLiteralExpression,
        range: TextRange,
        resolvedValue: String,
    ) {

        println()
        println("RN-HOVER: createOrdinaryReferences")
        println("===== Ordinary Reference =====")
        println("literal.textLength = ${literal.textLength}")
        println("literal.text = <${literal.text}>")
        println("range = $range")
        println("resolvedValue.length = ${resolvedValue.length}")
        println("resolvedValue = <$resolvedValue>")
    }
}
