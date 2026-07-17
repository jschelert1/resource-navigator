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

        val quoteStart =
            text.indexOfFirst {
                it == '"' || it == '\''
            }

        if (quoteStart < 0)
            return TextRange.EMPTY_RANGE

        val quoteLength =
            quoteLength(text)

        val contentStart =
            quoteStart + quoteLength

        val contentEnd =
            (text.length - quoteLength)
                .coerceAtLeast(contentStart)

        return TextRange(
            contentStart,
            contentEnd,
        )
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
     * Return the contents of a Python string literal, excluding prefixes and
     * surrounding quotation marks.
     */
    fun contentText(
        literal: PyStringLiteralExpression,
    ): String {

        val range =
            contentRange(literal)

        if (range.isEmpty)
            return ""

        return literal.text.substring(
            range.startOffset,
            range.endOffset,
        )
    }
}