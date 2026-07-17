package com.jschelert.resourcenavigator.navigation

import com.intellij.openapi.util.TextRange
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.util.PythonStringUtil

/**
 * =================================================================================================
 * ResourceCitation
 * =================================================================================================
 *
 * Navigation Model
 * ----------------
 * Represents a single bracketed resource citation discovered within a Python
 * string literal.
 *
 * Behavior
 * --------
 * • Stores the extracted citation text.
 * • Stores the corresponding range within the Python string literal.
 *
 * Responsibilities
 * ----------------
 * • Represent one parsed resource citation.
 * • Preserve the original source location for PSI navigation.
 */
data class ResourceCitation(
    val text: String,
    val range: TextRange,
)

/**
 * =================================================================================================
 * ResourceCitationParser
 * =================================================================================================
 *
 * Citation Parser
 * ---------------
 * Parses explicit bracketed resource citations embedded within Python string
 * literals.
 *
 * Behavior
 * --------
 * • Detects balanced bracketed resource references.
 * • Supports nested brackets within resource names.
 * • Returns one ResourceCitation for each balanced outer bracket pair.
 * • Maps parsed citations back to precise PSI text ranges.
 *
 * Supported Citation Examples
 * ---------------------------
 * ```text
 * [paper.pdf]
 * [figure.svg]
 * [C:\Temp\data.xlsx]
 * [Some Paper [1996].pdf]
 * ```
 *
 * Responsibilities
 * ----------------
 * • Parse bracketed resource references.
 * • Compute PSI text ranges.
 * • Locate the citation beneath a caret position.
 *
 * Dependencies
 * ------------
 * • PythonStringUtil
 * • PyStringLiteralExpression
 * • TextRange
 *
 * See Also
 * --------
 * • ResourceReferenceLocal
 * • ResourceReferenceUrl
 * • ResourceReferenceContributor
 *
 * Architectural Notes
 * -------------------
 * • Resource citations intentionally coexist with ordinary resource strings.
 *   This allows multiple independently navigable resources to be embedded
 *   within a single Python string literal.
 *
 * • Nested brackets are supported so that filenames containing bracketed text
 *   (for example publication years or revision numbers) remain valid citations.
 *
 * • Python string parsing is delegated entirely to PythonStringUtil. This class
 *   is responsible only for parsing the bracketed citation grammar.
 */
object ResourceCitationParser {

    /**
     * Parse every balanced bracketed resource citation contained within a
     * Python string literal.
     */
    fun parse(
        literal: PyStringLiteralExpression,
    ): List<ResourceCitation> {

        val content =
            PythonStringUtil.contentText(literal)

        if (content.isEmpty())
            return emptyList()

        val contentStart =
            PythonStringUtil.contentStartOffset(literal)

        val results =
            mutableListOf<ResourceCitation>()

        var depth = 0
        var start = -1

        content.forEachIndexed { index, ch ->

            when (ch) {

                '[' -> {

                    if (depth == 0)
                        start = index

                    depth++
                }

                ']' -> {

                    if (depth == 0)
                        return@forEachIndexed

                    depth--

                    if (depth == 0 && start >= 0) {

                        val resource =
                            content.substring(
                                start + 1,
                                index,
                            ).trim()

                        if (resource.isNotEmpty()) {

                            results += ResourceCitation(

                                text = resource,

                                range = TextRange(
                                    contentStart + start + 1,
                                    contentStart + index,
                                ),
                            )
                        }

                        start = -1
                    }
                }
            }
        }

        return results
    }

    /**
     * Return the citation containing the specified caret offset within the
     * Python string literal, or null if no bracketed citation is present.
     */
    fun findCitation(
        literal: PyStringLiteralExpression,
        offset: Int,
    ): ResourceCitation? {

        return parse(literal)
            .firstOrNull {

                offset in
                        it.range.startOffset until
                        it.range.endOffset
            }
    }
}