package com.jschelert.resourcenavigator.util

import com.intellij.openapi.util.TextRange
import com.jetbrains.python.psi.PyStringLiteralExpression

/**
 * =================================================================================================
 * PythonStringUtil
 * =================================================================================================
 *
 * Python String Utility
 * ---------------------
 * Shared utility functions for working with Python string literals represented
 * by the PyCharm PSI.
 *
 * Behavior
 * --------
 * • Determines the content range of Python string literals.
 * • Extracts the contents of Python string literals.
 * • Determines quote characters and quote lengths.
 * • Detects triple-quoted strings.
 * • Excludes Python prefixes and surrounding quotation marks.
 *
 * Responsibilities
 * ----------------
 * • Provide reusable Python string helper functions.
 * • Centralize Python string parsing logic.
 * • Eliminate duplicated PSI string handling throughout the plugin.
 *
 * Dependencies
 * ------------
 * • PyStringLiteralExpression
 * • TextRange
 *
 * See Also
 * --------
 * • ResourceReferenceContributor
 * • ResourceCitationParser
 * • PythonResourceContext
 *
 * Architectural Notes
 * -------------------
 * • This utility contains only generic Python string operations and should not
 *   contain resource-specific logic.
 *
 * • Resource recognition, path resolution, and navigation remain the
 *   responsibility of their respective subsystems.
 */
object PythonStringUtil {

    /**
     * Return the quote character (' or ") used by raw Python string text.
     */
    fun quoteCharacter(
        text: String,
    ): Char? {

        val quoteStart =
            text.indexOfFirst {
                it == '"' || it == '\''
            }

        if (quoteStart < 0)
            return null

        return text[quoteStart]
    }

    /**
     * Return true if the supplied raw Python string text uses triple quotes.
     */
    fun isTripleQuoted(
        text: String,
    ): Boolean {

        val quote =
            quoteCharacter(text)
                ?: return false

        val quoteStart =
            text.indexOf(quote)

        return text.startsWith(
            "$quote$quote$quote",
            quoteStart,
        )
    }

    /**
     * Return the opening/closing quote length (1 or 3) for raw Python string
     * text.
     */
    fun quoteLength(
        text: String,
    ): Int =
        if (isTripleQuoted(text)) {
            3
        } else {
            1
        }

    /**
     * Return the starting offset of the contents of a Python string literal,
     * excluding prefixes and surrounding quotation marks.
     */
    fun contentStartOffset(
        literal: PyStringLiteralExpression,
    ): Int =
        contentRange(literal).startOffset

    /**
     * Return the range occupied by the contents of a Python string literal,
     * excluding prefixes and surrounding quotation marks.
     */
    fun contentRange(
        literal: PyStringLiteralExpression,
    ): TextRange =
        contentRange(
            literal.text,
        )

    /**
     * Return the range occupied by the contents of raw Python string text,
     * excluding prefixes and surrounding quotation marks.
     */
    fun contentRange(
        text: String,
    ): TextRange {

        //
        // Locate the opening quote after any Python string prefix.
        //
        val quoteStart =
            text.indexOfFirst {
                it == '"' || it == '\''
            }

        //
        // Reject text containing no recognizable string delimiter.
        //
        if (quoteStart < 0)
            return TextRange.EMPTY_RANGE

        //
        // Determine whether the literal uses single or triple quotes.
        //
        val quoteLength =
            quoteLength(text)

        //
        // Advance beyond the opening quote sequence.
        //
        val contentStart =
            quoteStart + quoteLength

        //
        // Exclude the closing quote sequence while preventing an invalid range.
        //
        val contentEnd =
            (text.length - quoteLength)
                .coerceAtLeast(contentStart)

        //
        // Return the range containing only the Python string contents.
        //
        return TextRange(
            contentStart,
            contentEnd,
        )
    }

    /**
     * Return the source contents of a Python string literal, excluding prefixes
     * and surrounding quotation marks.
     *
     * Unlike PythonStringResolver, this function does not evaluate or reconstruct
     * the semantic compile-time value of the Python expression. It extracts the
     * contents directly from the original PSI source text.
     *
     * This distinction is important for IntelliJ editor/navigation behavior.
     * In particular, preserving the original source-oriented contents currently
     * allows the Go To Declaration/navigation pipeline to retain correct
     * correspondence with multiline and adjacent Python string literals.
     *
     * Passing only the reconstructed compile-time value through that pipeline
     * was observed to resolve the resource correctly while causing IntelliJ
     * Ctrl-hover highlighting to lose the complete multiline source range.
     * The underlying IntelliJ behavior is not yet fully understood, so this
     * source-preserving representation is intentionally retained.
     *
     * Example
     * -------
     *
     *     Python source:
     *
     *         path = (
     *             "docs/very-long-"
     *             "manual.pdf"
     *         )
     *
     *     contentText(literal):
     *
     *         docs/very-long-"
     *             "manual.pdf
     *
     *     PythonStringResolver:
     *
     *         docs/very-long-manual.pdf
     *
     * The first representation preserves physical source structure for PSI and
     * editor operations; the second represents the semantic Python value used
     * for resource classification and resolution.
     */
    fun contentText(
        literal: PyStringLiteralExpression,
    ): String {

        //
        // Determine the source range inside the literal's outer delimiters.
        //
        val range =
            contentRange(literal)

        //
        // Return an empty value when no valid content range is available.
        //
        if (range.isEmpty)
            return ""

        //
        // Extract the original source-oriented contents without evaluation.
        //
        return literal.text.substring(
            range.startOffset,
            range.endOffset,
        )
    }
}