package com.jschelert.resourcenavigator.util

import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyFormattedStringElement
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PyStringElement
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyParenthesizedExpression
import com.jschelert.resourcenavigator.config.ResourceNavigatorSettings

import java.nio.file.Paths

import kotlin.collections.mutableSetOf

/**
 * =================================================================================================
 * PythonStringResolver
 * =================================================================================================
 *
 * Compile-Time Python String Resolver
 * -----------------------------------
 * Evaluates supported compile-time Python string and path expressions represented
 * by the PyCharm PSI without executing Python code.
 *
 * The resolver reconstructs the semantic string value of supported expressions
 * and preserves the corresponding source-oriented representation through
 * PythonResolvedString.
 *
 * Resolution Tiers
 * ----------------
 *
 * +--------+--------------------------------------+----------------------------------------------+
 * | Tier   | Python Construct                     | Example                                      |
 * +--------+--------------------------------------+----------------------------------------------+
 * | Tier 1 | Ordinary string literal              | 'docs/manual.pdf'                            |
 * | Tier 1 | Raw string literal                   | r'C:\Docs\manual.pdf'                        |
 * | Tier 2 | Adjacent literal concatenation       | 'docs/' 'manual.pdf'                         |
 * | Tier 2 | Mixed raw/normal literals            | r'C:\Docs' '\manual.pdf'                     |
 * | Tier 3 | Constant f-string interpolation      | f'docs/{"manual"}.pdf'                       |
 * | Tier 3 | Nested constant expressions          | f'docs/{"man" + "ual"}.pdf'                  |
 * | Tier 4 | Constant variable reference          | NAME = 'manual.pdf'; path = NAME             |
 * | Tier 4 | Constant string concatenation        | BASE + '/manual.pdf'                         |
 * | Tier 4 | pathlib constructor                  | Path('docs/manual.pdf')                      |
 * | Tier 4 | Constant pathlib composition         | BASE / 'docs' / 'manual.pdf'                 |
 * +--------+--------------------------------------+----------------------------------------------+
 *
 * Supported Expression Types
 * --------------------------
 * • PyStringLiteralExpression
 *     Ordinary, raw, adjacent, and formatted Python string literals.
 *
 * • PyReferenceExpression
 *     References to compile-time constant assignments.
 *
 * • PyBinaryExpression
 *     Supported compile-time string and pathlib composition expressions.
 *
 * • PyCallExpression
 *     Supported compile-time pathlib constructor calls.
 *
 * Responsibilities
 * ----------------
 * • Evaluate supported Python expressions into compile-time string values.
 * • Reconstruct constant f-string interpolation recursively.
 * • Resolve references to compile-time constant assignments.
 * • Evaluate supported binary string and path expressions.
 * • Evaluate supported pathlib constructor expressions.
 * • Detect circular constant references.
 * • Reject runtime-dependent or unsupported expressions.
 * • Return both source-oriented and evaluated values through
 *   PythonResolvedString.
 *
 * Resolution Flow
 * ---------------
 *
 *     PyExpression
 *          |
 *          v
 *     resolve(expression)
 *          |
 *          +----> PyStringLiteralExpression
 *          |          |
 *          |          v
 *          |     evaluateStringLiteral()
 *          |
 *          +----> PyReferenceExpression
 *          |          |
 *          |          v
 *          |     evaluateConstantReference()
 *          |
 *          +----> PyBinaryExpression
 *          |          |
 *          |          v
 *          |     evaluateBinaryExpression()
 *          |
 *          +----> PyCallExpression
 *                     |
 *                     v
 *                evaluateConstantCall()
 *                     |
 *                     v
 *             recursive resolve()
 *                     |
 *                     v
 *            compile-time String
 *                     |
 *                     v
 *           PythonResolvedString
 *
 * Constant references are evaluated recursively. A resolving set tracks active
 * PyTargetExpression instances and prevents circular references from recursively
 * evaluating indefinitely.
 *
 * Example Resolution
 * ------------------
 *
 *     BASE = 'docs'
 *     NAME = 'manual'
 *     EXT  = '.pdf'
 *
 *     resource = Path(BASE) / f'{NAME}{EXT}'
 *
 * Conceptually resolves as:
 *
 *     BASE --------------------------> 'docs'
 *     NAME --------------------------> 'manual'
 *     EXT ---------------------------> '.pdf'
 *     f'{NAME}{EXT}' ----------------> 'manual.pdf'
 *     Path(BASE) --------------------> 'docs'
 *     Path(BASE) / f'{NAME}{EXT}' ---> 'docs/manual.pdf'
 *
 * Safety Model
 * ------------
 * • Python code is never executed.
 * • Only explicitly supported PSI expression types are evaluated.
 * • Runtime-dependent expressions return null.
 * • Function calls outside the supported compile-time set return null.
 * • Qualified or otherwise unsupported references return null.
 * • Circular constant references return null.
 *
 * Architectural Notes
 * -------------------
 * • PythonStringResolver is an evaluator, not a Python interpreter. Support is
 *   deliberately limited to expressions whose values can be determined safely
 *   and deterministically from PSI.
 *
 * • Expression handlers recursively invoke the internal resolve() function,
 *   allowing compound constant expressions to be evaluated from the same small
 *   set of supported primitives.
 *
 * • PythonResolvedString separates the physical/source-oriented representation
 *   from the reconstructed semantic value. Downstream PSI/editor operations can
 *   therefore preserve source correspondence while resource classification and
 *   resolution operate on the evaluated value.
 *
 * • Circular-reference detection is maintained across recursive evaluation so
 *   assignments such as A = B and B = A safely terminate without resolution.
 *
 * • Unsupported expressions fail closed by returning null rather than guessing
 *   or partially evaluating runtime-dependent Python behavior.
 *
 * • Resource classification and filesystem resolution remain outside this
 *   class. PythonStringResolver determines what a Python expression evaluates
 *   to; ResourceClassifier and ResourceResolver determine whether that value
 *   represents a navigable resource.
 *
 * See Also
 * --------
 * • PythonResolvedString
 * • PythonStringUtil
 * • PythonResourceContext
 * • ResourceClassifier
 * • ResourceResolver
 * • ResourceReferenceContributor
 *
 * Revision History
 * ----------------
 * v2.0.0 — 2026-07-23 (JS)
 * • Introduced recursive compile-time Python string evaluation.
 * • Added Tier 1 support for ordinary and raw Python string literals.
 * • Added Tier 2 support for adjacent and mixed Python string literals.
 * • Added Tier 3 support for constant f-string interpolation and nested
 *   compile-time expressions.
 * • Added Tier 4 support for constant references, binary string/path
 *   expressions, and supported pathlib constructors.
 * • Added circular-reference detection for recursively resolved constants.
 * • Introduced PythonResolvedString to preserve source-oriented and evaluated
 *   representations throughout the resource-resolution pipeline.
 * • Established a fail-closed evaluator that never executes Python code.
 * • Added transparent evaluation of parenthesized compile-time expressions.
 */
object PythonStringResolver {

    /**
     * Return the compile-time constant string represented by the supplied
     * Python expression, or null if the expression cannot be evaluated.
     */
    fun resolve(
        expression: PyExpression,
    ): PythonResolvedString? {

        //
        // Preserve the source-oriented representation of the expression.
        //
        val sourceString =
            when (expression) {

                is PyStringLiteralExpression ->
                    PythonStringUtil.contentText(expression)

                else ->
                    expression.text
            }

        //
        // Recursively evaluate the expression into its compile-time value.
        //
        val resolvedString =
            resolve(
                expression = expression,
                resolving = mutableSetOf(),
            )
                ?: return null

        //
        // Preserve both source-oriented and evaluated string representations.
        //
        return PythonResolvedString(
            sourceString = sourceString,
            resolvedString = resolvedString,
        )
    }

    /**
     * Dispatch a Python PSI expression to the appropriate compile-time evaluator.
     *
     * Expression Dispatch
     * -------------------
     *
     * +---------------------------+-------------------------------+-----------------------------+
     * | PSI Expression            | Python Example                | Evaluator                   |
     * +---------------------------+-------------------------------+-----------------------------+
     * | PyStringLiteralExpression | 'docs/manual.pdf'             | evaluateStringLiteral()     |
     * | PyReferenceExpression     | BASE                          | evaluateConstantReference() |
     * | PyBinaryExpression        | BASE + '/manual.pdf'          | evaluateBinaryExpression()  |
     * | PyCallExpression          | Path('docs/manual.pdf')       | evaluateConstantCall()      |
     * | Other expression          | runtime_function()            | null                        |
     * +---------------------------+-------------------------------+-----------------------------+
     *
     * Each evaluator may recursively invoke this dispatcher for child
     * expressions. The resolving set tracks active constant assignments and
     * prevents circular reference evaluation.
     */
    private fun resolve(
        expression: PyExpression,
        resolving: MutableSet<PyTargetExpression>,
    ): String? =
        when (expression) {

            //
            // Evaluate string literals, including formatted and adjacent strings.
            //
            // Example:
            //     'docs/manual.pdf'
            //
            is PyStringLiteralExpression ->
                evaluateStringLiteral(expression, resolving)

            //
            // Parentheses are transparent for compile-time evaluation.
            //
            is PyParenthesizedExpression ->
                expression.containedExpression?.let {
                    resolve(
                        it,
                        resolving,
                    )
                }

            //
            // Resolve references to compile-time constant assignments.
            //
            // Example:
            //     BASE
            //
            // where:
            //     BASE = 'docs'
            //
            is PyReferenceExpression ->
                evaluateConstantReference(expression, resolving)

            //
            // Evaluate supported compile-time binary expressions.
            //
            // Example:
            //     BASE + '/manual.pdf'
            //
            is PyBinaryExpression ->
                evaluateBinaryExpression(expression, resolving)

            //
            // Evaluate supported compile-time constructor calls.
            //
            // Example:
            //     Path('docs/manual.pdf')
            //
            is PyCallExpression ->
                evaluateConstantCall(expression, resolving)

            //
            // Reject unsupported or runtime-dependent expressions.
            //
            // Example:
            //     runtime_function()
            //
            else ->
                null
        }

    /**
     * Evaluate a Python string literal into its compile-time string value.
     *
     * String Literal Behavior
     * -----------------------
     *
     * +--------+--------------------------------------+-------------------------+------------------------+
     * | Tier   | Python Example                       | Evaluation Path         | Result                 |
     * +--------+--------------------------------------+-------------------------+------------------------+
     * | Tier 1 | 'docs/manual.pdf'                    | Fast path               | docs/manual.pdf        |
     * | Tier 1 | r'C:\Docs\manual.pdf'                | Fast path               | C:\Docs\manual.pdf     |
     * | Tier 2 | 'docs/' 'manual.pdf'                 | Fast path               | docs/manual.pdf        |
     * | Tier 2 | r'C:\Docs' '\manual.pdf'             | Fast path               | C:\Docs\manual.pdf     |
     * | Tier 3 | f'docs/manual.pdf'                   | Fast path               | docs/manual.pdf        |
     * | Tier 3 | f'docs/{"manual"}.pdf'               | Formatted-element path  | docs/manual.pdf        |
     * | Tier 3 | f'docs/{NAME}.pdf'                   | Formatted-element path  | Resolved NAME value    |
     * | Tier 3 | f'docs/{runtime()}.pdf'              | Formatted-element path  | null                   |
     * +--------+--------------------------------------+-------------------------+------------------------+
     *
     * Tier 1
     * ------
     * Ordinary and raw string literals are evaluated directly through
     * PyStringLiteralExpression.stringValue.
     *
     * Tier 2
     * ------
     * Adjacent Python string literals are represented by the same enclosing
     * PyStringLiteralExpression and are reconstructed by PyCharm into their
     * combined compile-time value.
     *
     * Tier 3
     * ------
     * F-strings without actual interpolation can still use the fast path.
     * Interpolated f-strings are reconstructed element-by-element, with each
     * embedded constant expression recursively evaluated through resolve().
     *
     * Returns null when any interpolated expression cannot be resolved as a
     * supported compile-time constant.
     *
     * Notes:
     * ------
     * Non-interpolated literals use PyCharm's stringValue directly. Interpolated
     * literals are reconstructed element-by-element so that constant f-string
     * expressions can be evaluated recursively.
     *
     * Returns null when any interpolated expression cannot be resolved as a
     * supported compile-time constant.
     */
    @Suppress(
        "UnstableApiUsage",
        "UsePropertyAccessSyntax",
    )
    private fun evaluateStringLiteral(
        literal: PyStringLiteralExpression,
        resolving: MutableSet<PyTargetExpression>,
    ): String? {

        //
        // Return PyCharm's evaluated value directly for non-interpolated strings.
        //
        if (!literal.isInterpolated())
            return literal.stringValue

        //
        // Accumulate the reconstructed value of the interpolated string.
        //
        val out =
            StringBuilder()

        //
        // Evaluate each constituent string element in source order.
        //
        for (element in literal.stringElements) {

            //
            // Dispatch formatted elements to the recursive f-string evaluator
            // and ordinary elements to decoded-fragment reconstruction.
            //
            val value =
                when (element) {

                    is PyFormattedStringElement ->
                        evaluateFormattedStringElement(
                            element,
                            resolving,
                        )

                    else ->
                        evaluateOrdinaryStringElement(element)
                }
                    ?: return null

            //
            // Append the evaluated element to the reconstructed string.
            //
            out.append(value)
        }

        //
        // Return the complete reconstructed compile-time string value.
        //
        return out.toString()
    }

    /**
     * Reconstruct the decoded value of an ordinary Python string element.
     *
     * String Element Behavior
     * -----------------------
     *
     * +--------+--------------------------------------+----------------------------------+
     * | Tier   | Python Example                       | Decoded Result                   |
     * +--------+--------------------------------------+----------------------------------+
     * | Tier 1 | 'docs/manual.pdf'                    | docs/manual.pdf                  |
     * | Tier 1 | 'docs\nmanual.pdf'                   | docs + newline + manual.pdf      |
     * | Tier 1 | r'C:\Docs\manual.pdf'                | C:\Docs\manual.pdf               |
     * | Tier 2 | 'docs/' 'manual.pdf'                 | docs/manual.pdf                  |
     * | Tier 3 | f'docs/{"manual"}.pdf'               | Ordinary portions: docs/ + .pdf |
     * +--------+--------------------------------------+----------------------------------+
     *
     * PyCharm exposes each ordinary string element as one or more decoded
     * fragments. Concatenating fragment.second reconstructs the semantic
     * string value represented by that element.
     *
     * This function performs no recursive expression evaluation. Formatted
     * f-string expressions are handled separately by
     * evaluateFormattedStringElement().
     */
    @Suppress("UnstableApiUsage")
    private fun evaluateOrdinaryStringElement(
        element: PyStringElement,
    ): String =
        buildString {

            //
            // Process each decoded fragment in source order.
            //
            for (fragment in element.decodedFragments) {

                //
                // Append the decoded semantic value of the fragment.
                //
                append(fragment.second)
            }
        }

    /**
     * Evaluate a formatted Python string element containing compile-time
     * constant interpolation.
     *
     * Tier 3 — Constant F-String Evaluation
     * -------------------------------------
     *
     * +--------+--------------------------------+-------------------+---------------------------+-------------------------+
     * | Tier   | Python                         | Evaluator Called? | decodedFragments          | Result                  |
     * +--------+--------------------------------+-------------------+---------------------------+-------------------------+
     * | Tier 3 | f"Hello"                       | No                | N/A                       | Hello                   |
     * | Tier 3 | f"{{hello}}"                   | No                | N/A                       | {hello}                 |
     * | Tier 3 | f"{'abc'}"                     | Yes               | "{'abc'}"                 | abc                     |
     * | Tier 3 | f"Hello {'abc'}"               | Yes               | "Hello ", "{'abc'}"       | Hello abc               |
     * | Tier 3 | f"Hello {'abc'}!"              | Yes               | "Hello ", "{'abc'}", "!"  | Hello abc!              |
     * | Tier 3 | f"{{{'abc'}}}"                 | Yes               | "{'abc'}"                 | {abc}                   |
     * | Tier 3 | f"{'manual.pdf'}"              | Yes               | "{'manual.pdf'}"          | manual.pdf              |
     * | Tier 3 | f"docs/{'manual.pdf'}"         | Yes               | "docs/", "{'manual.pdf'}" | docs/manual.pdf         |
     * | Tier 3 | f"{'https://example.com'}"     | Yes               | "{'https://example.com'}" | https://example.com     |
     * +--------+--------------------------------+-------------------+---------------------------+-------------------------+
     *
     * The first examples illustrate PyCharm f-string and decoded-fragment
     * behavior. The final examples illustrate values that may subsequently
     * become Resource Navigator references.
     *
     * A successfully evaluated string does not itself guarantee navigation.
     * The resulting value must still be accepted by ResourceClassifier and,
     * for local filesystem resources, resolve to an existing resource through
     * ResourceResolver.
     *
     * PyCharm exposes actual interpolation expressions through
     * formatted.fragments and exposes the surrounding decoded text through
     * formatted.decodedFragments.
     *
     * Interpolation fragments are matched with their corresponding formatted
     * expressions and recursively evaluated through resolve(). Ordinary decoded
     * fragments are appended directly to the reconstructed string.
     *
     * Escaped braces such as {{ and }} are not represented as independent
     * interpolation expressions. PyCharm incorporates them into the formatted
     * string representation, so they must not be mistaken for expressions that
     * require recursive evaluation.
     *
     * Returns null when an interpolation fragment has no corresponding
     * expression or when the embedded expression cannot be resolved as a
     * supported compile-time constant.
     */
    @Suppress("UnstableApiUsage")
    private fun evaluateFormattedStringElement(
        formatted: PyFormattedStringElement,
        resolving: MutableSet<PyTargetExpression>,
    ): String? {

        //
        // Accumulate the reconstructed formatted-string value.
        //
        val out =
            StringBuilder()

        //
        // Create an ordered iterator over the embedded f-string expressions.
        //
        val expressions =
            formatted.fragments.iterator()

        //
        // Process each decoded fragment in source order.
        //
        for (fragment in formatted.decodedFragments) {

            //
            // Extract the decoded fragment text.
            //
            val text =
                fragment.second

            //
            // Detect fragments representing actual f-string interpolation.
            //
            if (isInterpolationFragment(text)) {

                //
                // Require a corresponding formatted expression.
                //
                if (!expressions.hasNext())
                    return null

                //
                // Obtain the Python expression associated with this fragment.
                //
                val expression =
                    expressions.next().expression
                        ?: return null

                //
                // Recursively evaluate the interpolation expression.
                //
                val value =
                    resolve(
                        expression,
                        resolving,
                    )
                        ?: return null

                //
                // Append the evaluated interpolation value.
                //
                out.append(value)
            }
            else {

                //
                // Append ordinary decoded text directly.
                //
                out.append(text)
            }
        }

        //
        // Return the complete reconstructed f-string value.
        //
        return out.toString()
    }

    /**
     * Return true if the decoded fragment represents an f-string interpolation
     * fragment.
     *
     * Tier 3 — Interpolation Fragment Detection
     * -----------------------------------------
     *
     * +---------------------------+------------------------+--------+
     * | Fragment Text             | Interpretation         | Result |
     * +---------------------------+------------------------+--------+
     * | "{'manual.pdf'}"          | Interpolation fragment | true   |
     * | "{NAME}"                  | Interpolation fragment | true   |
     * | "{BASE + NAME}"           | Interpolation fragment | true   |
     * | "docs/"                   | Ordinary decoded text  | false  |
     * | "manual.pdf"              | Ordinary decoded text  | false  |
     * | ""                        | Empty fragment          | false  |
     * | "{"                       | Incomplete braces      | false  |
     * | "}"                       | Incomplete braces      | false  |
     * +---------------------------+------------------------+--------+
     *
     * This function performs only structural detection. It does not parse or
     * evaluate the expression contained between the braces.
     *
     * When true is returned, evaluateFormattedStringElement() obtains the
     * corresponding PyFStringFragment expression and recursively evaluates it
     * through resolve().
     *
     * Escaped literal braces are handled by PyCharm's formatted-string PSI and
     * are not independently evaluated by this function as interpolation
     * expressions.
     */
    private fun isInterpolationFragment(
        text: String,
    ): Boolean =

    //
    // Require at least one opening and one closing brace.
        //
        text.length >= 2 &&

                //
                // Require the fragment to begin with an opening brace.
                //
                text.first() == '{' &&

                //
                // Require the fragment to end with a closing brace.
                //
                text.last() == '}'

    /**
     * Resolve a reference to a compile-time constant assignment.
     *
     * I'd rank our constraints:
     *
     * 1.  Never interfere with normal PyCharm variable/declaration navigation.
     * 2.  Tier 4 resolves the complete compile-time resource correctly.
     * 3.  Only appropriate string-literal portions become Resource Navigator links.
     * 4.  Ctrl+Click, inspection and Quick Documentation agree on the resolved target.
     * 5.  Unified adjacent/multiline highlighting if IntelliJ lets us preserve it cleanly.
     *
     * Tier 4 — Constant Reference Resolution
     * --------------------------------------
     *
     * +-------------------------------+-------------------------+------------------------+
     * | Python Example                | Assigned Value          | Result                 |
     * +-------------------------------+-------------------------+------------------------+
     * | NAME = 'manual.pdf'; NAME     | 'manual.pdf'            | manual.pdf             |
     * | BASE = 'docs'; BASE           | 'docs'                  | docs                   |
     * | A = 'docs'; B = A; B          | A                       | docs                   |
     * | NAME = f'{"manual"}.pdf'; NAME| Constant f-string       | manual.pdf             |
     * | A = B; B = A; A               | Circular reference      | null                   |
     * | obj.NAME                      | Qualified reference     | null                   |
     * | UNKNOWN                       | Unresolved reference    | null                   |
     * +-------------------------------+-------------------------+------------------------+
     *
     * Only unqualified references are evaluated. The reference must resolve to
     * a PyTargetExpression with an assigned value that can itself be recursively
     * evaluated by resolve().
     *
     * The resolving set tracks assignments currently participating in recursive
     * evaluation. Encountering the same target again indicates a circular
     * reference and causes evaluation to fail safely with null.
     *
     * This function never executes Python code. Unsupported, unresolved,
     * qualified, runtime-dependent, or circular references return null.
     */
    private fun evaluateConstantReference(
        reference: PyReferenceExpression,
        resolving: MutableSet<PyTargetExpression>,
    ): String? {

        //
        // Reject qualified references such as object.NAME.
        //
        if (reference.qualifier != null)
            return null

        //
        // Resolve the reference to its Python assignment target.
        //
        val target =
            reference.reference.resolve() as? PyTargetExpression
                ?: return null

        //
        // Register the target and reject circular reference chains.
        //
        if (!resolving.add(target))
            return null

        return try {

            //
            // Obtain the expression assigned to the constant.
            //
            val assignedValue =
                target.findAssignedValue()
                    ?: return null

            //
            // Emit Tier 4 constant-reference diagnostics when global diagnostics are enabled.
            //
            if (ResourceNavigatorSettings.DIAGNOSTICS_ENABLED) {
                diagnoseConstantReference(
                    reference = reference,
                    target = target,
                    assignedValue = assignedValue,
                )
            }

            //
            // Recursively evaluate the assigned expression.
            //
            resolve(
                assignedValue,
                resolving,
            )
        }
        finally {

            //
            // Remove the target after this resolution branch completes.
            //
            resolving.remove(target)
        }
    }

    /**
     * Evaluate a supported compile-time binary string or path expression.
     *
     * Tier 4 — Binary Expression Evaluation
     * -------------------------------------
     *
     * +--------------------------------------+------------+--------------------------+
     * | Python Example                       | Operator   | Result                   |
     * +--------------------------------------+------------+--------------------------+
     * | 'docs/' + 'manual.pdf'               | +          | docs/manual.pdf          |
     * | BASE + '/manual.pdf'                 | +          | docs/manual.pdf          |
     * | BASE / 'manual.pdf'                  | /          | docs/manual.pdf          |
     * | BASE / 'papers' / 'manual.pdf'       | /          | docs/papers/manual.pdf   |
     * | BASE - 'manual.pdf'                  | -          | null                     |
     * | BASE + runtime()                     | +          | null                     |
     * +--------------------------------------+------------+--------------------------+
     *
     * Both operands are recursively evaluated through resolve(). String
     * concatenation uses the Python + operator semantics required by Resource
     * Navigator, while the / operator represents pathlib-style path composition.
     *
     * Nested binary expressions are naturally supported because each operand
     * may itself be another PyBinaryExpression.
     *
     * Returns null when the operator is unsupported or either operand cannot
     * be evaluated as a supported compile-time string expression.
     */
    @Suppress("UnstableApiUsage")
    private fun evaluateBinaryExpression(
        expression: PyBinaryExpression,
        resolving: MutableSet<PyTargetExpression>,
    ): String? {

        //
        // Obtain both operands of the binary expression.
        //
        val leftExpression =
            expression.leftExpression

        val rightExpression =
            expression.rightExpression
                ?: return null

        //
        // Recursively evaluate the left operand.
        //
        val left =
            resolve(
                leftExpression,
                resolving,
            ) ?: return null

        //
        // Recursively evaluate the right operand.
        //
        val right =
            resolve(
                rightExpression,
                resolving,
            ) ?: return null

        //
        // Evaluate the supported compile-time binary operator.
        //
        return when (expression.operator) {

            //
            // Concatenate compile-time string values.
            //
            PyTokenTypes.PLUS ->
                left + right

            //
            // Compose pathlib-style path components.
            //
            PyTokenTypes.DIV ->
                Paths.get(left)
                    .resolve(right)
                    .toString()

            //
            // Reject unsupported binary operators.
            //
            else ->
                null
        }
    }

    /**
     * Evaluate a supported compile-time pathlib constructor call.
     *
     * Tier 4 — Constant Call Evaluation
     * ---------------------------------
     *
     * +------------------------------------------+---------------------+------------------------+
     * | Python Example                           | Constructor         | Result                 |
     * +------------------------------------------+---------------------+------------------------+
     * | Path('docs/manual.pdf')                  | Path                | docs/manual.pdf        |
     * | PurePath('docs/manual.pdf')              | PurePath            | docs/manual.pdf        |
     * | PureWindowsPath(r'C:\Docs\manual.pdf')   | PureWindowsPath     | C:\Docs\manual.pdf     |
     * | PurePosixPath('/docs/manual.pdf')        | PurePosixPath       | /docs/manual.pdf       |
     * | Path(BASE)                               | Path                | Resolved BASE value    |
     * | Path(f'docs/{"manual"}.pdf')             | Path                | docs/manual.pdf        |
     * | pathlib.Path('docs/manual.pdf')          | Path                | docs/manual.pdf        |
     * | Path(runtime())                          | Path                | null                   |
     * | Path('docs', 'manual.pdf')               | Multiple arguments  | null                   |
     * | open('manual.pdf')                       | Unsupported call    | null                   |
     * +------------------------------------------+---------------------+------------------------+
     *
     * Supported pathlib constructors are treated as compile-time wrappers
     * around a single resolvable string expression. The argument is recursively
     * evaluated through resolve(), allowing literals, constant references,
     * constant f-strings, and other supported expressions to participate.
     *
     * Qualified pathlib calls such as pathlib.Path(...) are supported by
     * comparing only the final component of the callee expression.
     *
     * This function does not execute Python code or invoke pathlib. It returns
     * the recursively evaluated constructor argument as the compile-time value.
     *
     * Returns null for unsupported calls, missing arguments, multiple
     * arguments, or arguments that cannot be evaluated at compile time.
     */
    private fun evaluateConstantCall(
        expression: PyCallExpression,
        resolving: MutableSet<PyTargetExpression>,
    ): String? {

        //
        // Obtain the called expression.
        //
        val callee =
            expression.callee
                ?: return null

        //
        // Extract the final constructor name from qualified or unqualified calls.
        //
        val constructor =
            callee.text
                .substringAfterLast('.')

        //
        // Accept only supported pathlib constructors.
        //
        if (!PythonResourceContext.isPathConstructorName(constructor))
            return null

        //
        // Obtain the positional arguments supplied to the constructor.
        //
        val arguments =
            expression.arguments

        //
        // Require exactly one compile-time constructor argument.
        //
        if (arguments.size != 1)
            return null

        //
        // Obtain the constructor argument as a Python expression.
        //
        val argument =
            arguments.single()
                ?: return null

        //
        // Recursively evaluate and return the constructor argument.
        //
        return resolve(
            argument,
            resolving,
        )
    }

    /**
     * Print diagnostic information for a resolved compile-time constant
     * reference and its assigned expression.
     *
     * This helper is intended for temporary evaluator diagnostics while
     * developing or debugging Tier 4 constant-reference resolution.
     */
    private fun diagnoseConstantReference(
        reference: PyReferenceExpression,
        target: PyTargetExpression,
        assignedValue: PyExpression,
    ) {

        println()
        println("===== Constant Reference =====")
        println("reference       = <${reference.text}>")
        println("target          = <${target.text}>")
        println("target class    = ${target.javaClass.name}")
        println("assigned text   = <${assignedValue.text}>")
        println("assigned class  = ${assignedValue.javaClass.name}")
    }
}
