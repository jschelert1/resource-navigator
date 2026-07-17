package com.jschelert.resourcenavigator.navigation

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.util.PythonStringUtil
import com.jschelert.resourcenavigator.util.ResourceClassifier
import com.jschelert.resourcenavigator.util.ResourceResolver

/**
 * =================================================================================================
 * ResourceReferenceContributor
 * =================================================================================================
 *
 * PSI Reference Contributor
 * -------------------------
 * Registers Resource Navigator PSI reference providers for Python string
 * literals and constructs the appropriate resource references recognized by
 * the plugin.
 *
 * Behavior
 * --------
 * • Registers a PSI reference provider for Python string literals.
 * • Gives precedence to bracketed resource citations.
 * • Preserves ordinary one-resource-per-string navigation.
 * • Creates either ResourceReferenceLocal or ResourceReferenceUrl objects.
 * • Filters candidates using ResourceClassifier.
 * • Resolves local filesystem resources before creating navigation targets.
 *
 * Responsibilities
 * ----------------
 * • Register IntelliJ PSI reference providers.
 * • Dispatch between citation and ordinary resource handling.
 * • Construct PSI references for recognized resources.
 * • Coordinate resource classification and resolution.
 *
 * Dependencies
 * ------------
 * • PythonStringUtil
 * • ResourceCitationParser
 * • ResourceClassifier
 * • ResourceResolver
 * • ResourceReferenceLocal
 * • ResourceReferenceUrl
 * • IntelliJ PSI Reference APIs
 *
 * See Also
 * --------
 * • PythonStringUtil
 * • ResourceCitationParser
 * • ResourceReferenceLocal
 * • ResourceReferenceUrl
 * • ResourceGotoDeclarationHandler
 *
 * Architectural Notes
 * -------------------
 * • Bracketed citations intentionally take precedence over ordinary resource
 *   strings, allowing multiple independently navigable resources to exist
 *   within a single Python string literal.
 *
 * • Python string parsing has been delegated entirely to PythonStringUtil.
 *   This class coordinates navigation behavior rather than parsing Python
 *   syntax.
 *
 * • ResourceReferenceContributor serves as the central dispatcher between the
 *   PSI infrastructure and the Resource Navigator navigation subsystem.
 */

class ResourceReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(
        registrar: PsiReferenceRegistrar,
    ) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(
                PyStringLiteralExpression::class.java,
            ),
            object : PsiReferenceProvider() {

                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {

                    val literal =
                        element as? PyStringLiteralExpression
                            ?: return PsiReference.EMPTY_ARRAY

                    /*
                     * Bracketed citations take precedence over the normal
                     * one-resource-per-string behavior.
                     *
                     * Examples:
                     *
                     *     [paper.pdf]
                     *     [Some Article [1996].pdf]
                     *     [C:\Documents\results.xlsx]
                     */
                    val citations =
                        ResourceCitationParser.parse(literal)

                    if (citations.isNotEmpty()) {
                        return createCitationReferences(
                            literal = literal,
                            citations = citations,
                        )
                    }

                    return createOrdinaryReferences(literal)

                }
            }
        )
    }

    /**
     * Create the PSI reference for an ordinary Python string containing one
     * complete resource.
     *
     * Behavior
     * --------
     * • Validates the resource using ResourceClassifier.
     * • Creates URL references for HTTP/HTTPS resources.
     * • Resolves local filesystem resources.
     * • Returns an empty array when navigation is not applicable.
     */
    private fun createOrdinaryReferences(
        literal: PyStringLiteralExpression,
    ): Array<PsiReference> {

        val value =
            PythonStringUtil.contentText(literal).trim()

        val shouldHandle =
            ResourceClassifier.shouldHandle(
                literal,
                value,
            )

        if (!shouldHandle)
            return PsiReference.EMPTY_ARRAY

        val range =
            PythonStringUtil.contentRange(literal)

        if (ResourceClassifier.isUrl(value)) {
            return arrayOf(
                ResourceReferenceUrl(
                    literal,
                    range,
                )
            )
        }

        val target =
            ResourceResolver.resolve(literal)
                ?: return PsiReference.EMPTY_ARRAY

        if (!target.exists) {
            return PsiReference.EMPTY_ARRAY
        }

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
     * • Skips invalid or unresolved resources.
     * • Returns one PSI reference per valid citation.
     *
     * Known limitation: For bracketed resource citations inside Python
     * triple-quoted strings, PyCharm may highlight the entire docstring
     * while Ctrl+Click is held. Navigation is unaffected and opens the
     * correct resource. This appears to be an editor presentation behavior
     * rather than a limitation of resource resolution.
     */
    private fun createCitationReferences(
        literal: PyStringLiteralExpression,
        citations: List<ResourceCitation>,
    ): Array<PsiReference> {

        val references =
            mutableListOf<PsiReference>()

        citations.forEach { citation ->

            val value =
                citation.text.trim()

            if (value.isEmpty())
                return@forEach

            /*
             * Brackets are not required for URLs, but accepting a bracketed
             * URL remains harmless and keeps the citation parser general.
             */
            if (ResourceClassifier.isUrl(value)) {

                references +=
                    ResourceReferenceUrl(
                        literal,
                        citation.range,
                    )

                return@forEach
            }

            val shouldHandle =
                ResourceClassifier.shouldHandle(
                    literal,
                    value,
                )

            if (!shouldHandle) {
                return@forEach
            }

            val target =
                ResourceResolver.resolveLocal(
                    project = literal.project,
                    containingFile = literal.containingFile,
                    raw = value,
                )

            if (!target.exists)
                return@forEach

            references +=
                ResourceReferenceLocal(
                    element = literal,
                    range = citation.range,
                    rawValue = citation.text,
                )
        }

        return references.toTypedArray()
    }
}
