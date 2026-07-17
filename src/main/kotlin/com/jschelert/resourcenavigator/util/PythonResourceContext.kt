package com.jschelert.resourcenavigator.util

import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyStringLiteralExpression

/**
 * =================================================================================================
 * PythonResourceContext
 * =================================================================================================
 *
 * Python Resource Context
 * -----------------------
 * Provides helper functions for determining the syntactic context of Python
 * string literals within the PSI tree. These context checks assist Resource
 * Navigator in deciding whether a string should be interpreted as a resource.
 *
 * Behavior
 * --------
 * • Detects pathlib constructor arguments.
 * • Identifies supported pathlib wrapper types.
 * • Operates entirely on Python PSI.
 * • Performs no resource classification or path resolution.
 *
 * Responsibilities
 * ----------------
 * • Inspect Python PSI context.
 * • Recognize supported pathlib constructors.
 * • Provide reusable syntax queries for resource classification.
 *
 * Dependencies
 * ------------
 * • PyStringLiteralExpression
 * • PyCallExpression
 *
 * See Also
 * --------
 * • ResourceClassifier
 * • PythonStringUtil
 * • ResourceResolver
 * • ResourceReferenceContributor
 *
 * Architectural Notes
 * -------------------
 * • This utility is intentionally limited to Python syntax inspection.
 *
 * • Resource classification, path resolution, and navigation remain the
 *   responsibility of their respective subsystems.
 *
 * • New Python syntactic contexts (for example additional constructors or
 *   language constructs) should be added here rather than distributed
 *   throughout the navigation codebase.
 */
object PythonResourceContext {

    /**
     * Return true if the supplied string literal is the argument to a supported
     * pathlib constructor.
     *
     * Examples
     * --------
     *     Path("file.pdf")
     *     PurePath("file.pdf")
     *     PureWindowsPath("file.pdf")
     *     PurePosixPath("file.pdf")
     */
    fun isPathWrapped(
        element: PyStringLiteralExpression,
    ): Boolean {

        val call =
            element.parent as? PyCallExpression
                ?: return false

        return isPathConstructor(call)
    }

    /**
     * Return true if the supplied call expression represents a supported
     * pathlib constructor.
     */
    private fun isPathConstructor(
        call: PyCallExpression,
    ): Boolean {

        val name =
            call.callee
                ?.text
                ?.substringAfterLast('.')

        return name in setOf(
            "Path",
            "PurePath",
            "PureWindowsPath",
            "PurePosixPath",
        )
    }
}