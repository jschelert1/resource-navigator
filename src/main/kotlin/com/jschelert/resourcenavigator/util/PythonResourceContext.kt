package com.jschelert.resourcenavigator.util

import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyStringLiteralExpression

/**
 * =================================================================================================
 * PythonResourceContext
 * =================================================================================================
 *
 * Python Resource Context
 * -----------------------
 * Provides helper functions for determining the syntactic and evaluation
 * context of Python string literals within the PSI tree. These context checks
 * assist Resource Navigator in determining how a string literal participates
 * in a supported resource expression.
 *
 * Behavior
 * --------
 * • Detects Python documentation/docstring string literals.
 * • Detects pathlib constructor arguments.
 * • Identifies supported pathlib wrapper types.
 * • Determines the enclosing compile-time expression for resource evaluation.
 * • Expands through supported + and / binary expressions.
 * • Preserves the original string literal as the navigation/highlight source.
 * • Operates entirely on Python PSI.
 * • Performs no resource classification or path resolution.
 *
 * Responsibilities
 * ----------------
 * • Inspect Python PSI context.
 * • Identify documentation strings shared by navigation and inspection.
 * • Recognize supported pathlib constructors.
 * • Determine supported compile-time evaluation scope.
 * • Preserve separation between evaluation scope and hyperlink source.
 * • Provide reusable syntax queries for resource processing.
 *
 * Dependencies
 * ------------
 * • PyStringLiteralExpression
 * • PyBinaryExpression
 * • PyCallExpression
 * • PyExpression
 *
 * See Also
 * --------
 * • PythonStringResolver
 * • PythonStringUtil
 * • ResourceClassifier
 * • ResourceResolver
 * • ResourceReferenceContributor
 *
 * Architectural Notes
 * -------------------
 * • This utility is intentionally limited to Python syntax and PSI context
 *   inspection.
 *
 * • Evaluation scope may extend beyond the original string literal, while
 *   navigation and highlighting remain attached to that literal.
 *
 * • Compile-time string evaluation, resource classification, path resolution,
 *   and navigation remain the responsibility of their respective subsystems.
 *
 * • New Python syntactic contexts (for example additional constructors,
 *   operators, or language constructs) should be added here rather than
 *   distributed throughout the navigation codebase.
 *
 * Revision History
 * ----------------
 * v1.1.0 — 2026-10-02 (JS)
 * • Added shared Python docstring detection for navigation and missing-resource inspection.
 * • Established docstrings as documentation unless a resource is explicitly bracketed.
 *
 *   PyStringLiteralExpression
 *         │
 *         ├── hyperlink/source range ────────────────┐
 *         │                                          │
 *         └── PythonResourceContext                  │
 *                 │                                  │
 *                 ▼                                  │
 *         enclosing supported expression            │
 *                 │                                  │
 *                 ▼                                  │
 *         PythonStringResolver                       │
 *                 │                                  │
 *                 ▼                                  │
 *         complete resolved resource                 │
 *                 │                                  │
 *                 └──────── ResourceReferenceLocal ◄─┘
 *
 */
object PythonResourceContext {

    /**
     * Return true when the supplied Python string literal is a documentation string.
     *
     * Resource Navigator treats docstrings as documentation by default. Explicit
     * bracketed citations remain opt-in resources, while quoted and ordinary path-like
     * text inside the docstring is ignored by navigation and missing-resource inspection.
     */
    fun isDocumentationString(
        element: PyStringLiteralExpression,
    ): Boolean =

        element.stringElements.any { stringElement ->
            stringElement.node.elementType == PyTokenTypes.DOCSTRING
        }

    /**
     * Return true if the supplied string literal is the argument to a supported
     * pathlib constructor.
     *
     * Path Wrapper Behavior
     * ---------------------
     *
     * +------------------------------------------+--------------------------+--------+
     * | Python Example                           | Constructor              | Result |
     * +------------------------------------------+--------------------------+--------+
     * | Path('file.pdf')                         | Path                     | true   |
     * | PurePath('file.pdf')                     | PurePath                 | true   |
     * | PureWindowsPath('C:\Docs\file.pdf')      | PureWindowsPath          | true   |
     * | PurePosixPath('/docs/file.pdf')          | PurePosixPath            | true   |
     * | pathlib.Path('file.pdf')                 | Path                     | true   |
     * | open('file.pdf')                         | Unsupported constructor  | false  |
     * | 'file.pdf'                               | No constructor           | false  |
     * +------------------------------------------+--------------------------+--------+
     *
     * Qualified constructor names are supported because only the final
     * component of the call expression is compared against the recognized
     * pathlib constructor names.
     *
     * “Wrapped” here means the string literal is directly enclosed as the argument
     * of a Path-type constructor.
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
     * Return true if the supplied call invokes a supported pathlib constructor.
     */
    private fun isPathConstructor(
        call: PyCallExpression,
    ): Boolean {

        //
        // Obtain the called expression.
        //
        val callee =
            call.callee
                ?: return false

        //
        // Extract the final component of qualified or unqualified call names.
        //
        val name =
            callee.text
                .substringAfterLast('.')

        //
        // Determine whether the constructor is supported.
        //
        return isPathConstructorName(name)
    }

    /**
     * Return true if the supplied name identifies a supported pathlib
     * constructor.
     *
     * +---------------------+--------+
     * | Constructor Name    | Result |
     * +---------------------+--------+
     * | Path                | true   |
     * | PurePath            | true   |
     * | PureWindowsPath     | true   |
     * | PurePosixPath       | true   |
     * | open                | false  |
     * | File                | false  |
     * +---------------------+--------+
     *
     * This provides a shared constructor-name predicate for components that
     * need to recognize pathlib constructors without duplicating the
     * authoritative pathConstructors set.
     */
    fun isPathConstructorName(
        name: String,
    ): Boolean =

        name in pathConstructors

    /**
     * Supported pathlib constructor names recognized as compile-time
     * path wrappers by Resource Navigator.
     */
    private val pathConstructors =
        setOf(
            "Path",
            "PurePath",
            "PureWindowsPath",
            "PurePosixPath",
        )

    /**
     * Return the supported enclosing expression that should be evaluated to
     * determine the resource represented by the supplied string literal.
     *
     * The returned expression defines evaluation scope only. Resource Navigator
     * continues to attach navigation and highlighting to the original string
     * literal so that surrounding Python references retain their normal PyCharm
     * declaration-navigation behavior.
     *
     * Examples:
     *
     *     BASE_PATH + r"\icons\info.svg"
     *                 ^^^^^^^^^^^^^^^^^^
     *
     *     r"C:\Projects\App" + INFO_SVG
     *     ^^^^^^^^^^^^^^^^^^
     *
     * In both cases, the string literal remains the Resource Navigator hyperlink,
     * while the enclosing binary expression is returned for compile-time
     * evaluation. Symbolic components such as BASE_PATH and INFO_SVG retain their
     * normal PyCharm declaration-navigation behavior.
     *
     * Supported evaluation contexts:
     *
     * • The original string literal.
     * • Enclosing + and / binary expressions when the current expression
     *   occupies either side of the binary expression.
     * • A supported Path/PurePath constructor wrapping such an expression.
     *
     * Binary expressions may be expanded repeatedly to obtain the complete
     * supported compile-time expression before an optional pathlib wrapper is
     * included.
     *
     * Unsupported parent expressions terminate upward traversal.
     */
    @Suppress("UnstableApiUsage")
    fun evaluationExpression(
        literal: PyStringLiteralExpression,
    ): PyExpression {

        var expression: PyExpression =
            literal

        //
        // Expand through supported compile-time binary expressions.
        //
        while (true) {

            val binary =
                expression.parent as? PyBinaryExpression
                    ?: break

            //
            // Accept only operators supported by PythonStringResolver.
            //
            if (
                binary.operator != PyTokenTypes.PLUS &&
                binary.operator != PyTokenTypes.DIV
            ) {
                break
            }

            //
            // Expand when the current expression occupies either side of the
            // supported binary expression. The original string literal remains
            // the Resource Navigator hyperlink, while symbolic components retain
            // their normal PyCharm declaration-navigation behavior.
            //
            if (
                binary.leftExpression != expression &&
                binary.rightExpression != expression
            ) {
                break
            }

            expression = binary
        }

        //
        // Expand once more when the resulting expression is wrapped directly
        // by a supported pathlib constructor.
        //
        val call =
            expression.parent as? PyCallExpression

        if (
            call != null &&
            call.arguments.size == 1 &&
            call.arguments.single() == expression &&
            isPathConstructor(call)
        ) {
            expression = call
        }

        return expression
    }
}