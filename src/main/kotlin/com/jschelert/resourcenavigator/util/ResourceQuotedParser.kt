package com.jschelert.resourcenavigator.util

import com.intellij.openapi.util.TextRange
import com.jetbrains.python.psi.PyStringLiteralExpression

/**
 * =================================================================================================
 * ResourceQuotedParser
 * =================================================================================================
 *
 * Embedded Quoted Resource Parser
 * -------------------------------
 * Extracts explicitly double-quoted resource tokens embedded inside Python
 * descriptive strings while preserving physical PSI ranges.
 *
 * Behavior
 * --------
 * • Reconstructs semantic content across adjacent Python string elements.
 * • Finds complete double-quoted tokens in the reconstructed string.
 * • Maps each quoted token back to one or more physical PSI ranges.
 * • Preserves one complete resource value across all source fragments.
 * • Does not infer unquoted resources embedded in prose.
 *
 * Example
 * -------
 *
 *     (
 *         r'Open "C:\Documents'
 *         r'\report.pdf" for details'
 *     )
 *
 * Semantic resource:
 *
 *     C:\Documents\report.pdf
 *
 * Physical ranges:
 *
 *     C:\Documents
 *     \report.pdf
 *
 * Revision History
 * ----------------
 * v1.0.0 — 2026-08-25 (JS)
 * • Extracted embedded quoted-resource parsing from ResourceReferenceContributor.
 * • Added shared semantic-to-PSI range mapping for navigation and inspection.
 */
object ResourceQuotedParser {

    /**
     * Resource token explicitly quoted inside the semantic content of a Python
     * string expression.
     */
    data class QuotedResource(
        val text: String,
        val ranges: List<TextRange>,
    )

    /**
     * Source fragment contributing text to a reconstructed Python string.
     */
    private data class StringFragment(
        val semanticStart: Int,
        val semanticEnd: Int,
        val sourceRange: TextRange,
    )

    /**
     * Parse explicitly quoted resources from a Python string expression.
     */
    fun parse(
        literal: PyStringLiteralExpression,
    ): List<QuotedResource> {

        val fragments =
            mutableListOf<StringFragment>()

        val semantic =
            StringBuilder()

        literal.stringElements.forEach { element ->

            val body =
                stringElementBody(element.text)
                    ?: return@forEach

            val elementValue =
                body.first

            val contentStart =
                body.second

            val semanticStart =
                semantic.length

            semantic.append(elementValue)

            fragments +=
                StringFragment(
                    semanticStart = semanticStart,
                    semanticEnd = semantic.length,
                    sourceRange = TextRange(
                        element.textRange.startOffset - literal.textRange.startOffset + contentStart,
                        element.textRange.startOffset - literal.textRange.startOffset + contentStart + elementValue.length,
                    ),
                )
        }

        if (fragments.isEmpty()) {
            return emptyList()
        }

        val pattern =
            Regex("\"([^\"]+)\"")

        return pattern
            .findAll(semantic.toString())
            .mapNotNull { match ->

                val group =
                    match.groups[1]
                        ?: return@mapNotNull null

                val resourceStart =
                    group.range.first

                val resourceEnd =
                    group.range.last + 1

                val ranges =
                    fragments.mapNotNull { fragment ->

                        val overlapStart =
                            maxOf(
                                resourceStart,
                                fragment.semanticStart,
                            )

                        val overlapEnd =
                            minOf(
                                resourceEnd,
                                fragment.semanticEnd,
                            )

                        if (overlapStart >= overlapEnd) {
                            return@mapNotNull null
                        }

                        val localStart =
                            overlapStart - fragment.semanticStart

                        val localEnd =
                            overlapEnd - fragment.semanticStart

                        TextRange(
                            fragment.sourceRange.startOffset + localStart,
                            fragment.sourceRange.startOffset + localEnd,
                        )
                    }

                if (ranges.isEmpty()) {
                    return@mapNotNull null
                }

                QuotedResource(
                    text = group.value,
                    ranges = ranges,
                )
            }
            .toList()
    }

    /**
     * Return the physical body and body-start offset for one Python string
     * element.
     */
    private fun stringElementBody(
        text: String,
    ): Pair<String, Int>? {

        var quoteStart =
            0

        while (
            quoteStart < text.length &&
            text[quoteStart].isLetter()
        ) {
            quoteStart++
        }

        if (quoteStart >= text.length) {
            return null
        }

        val quote =
            text[quoteStart]

        if (quote != '\'' && quote != '"') {
            return null
        }

        val quoteLength =
            if (
                quoteStart + 2 < text.length &&
                text[quoteStart + 1] == quote &&
                text[quoteStart + 2] == quote
            ) {
                3
            } else {
                1
            }

        val bodyStart =
            quoteStart + quoteLength

        val bodyEnd =
            text.length - quoteLength

        if (bodyEnd < bodyStart) {
            return null
        }

        return Pair(
            text.substring(
                bodyStart,
                bodyEnd,
            ),
            bodyStart,
        )
    }
}