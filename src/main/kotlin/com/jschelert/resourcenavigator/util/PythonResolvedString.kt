package com.jschelert.resourcenavigator.util

/**
 * =================================================================================================
 * PythonResolvedString
 * =================================================================================================
 *
 * Resolved Python String
 * ----------------------
 * Represents both the source-oriented and compile-time evaluated forms of a
 * Python string handled by Resource Navigator.
 *
 * Behavior
 * --------
 * • Preserves the original source-oriented string representation.
 * • Stores the reconstructed compile-time string value.
 * • Separates PSI/editor concerns from semantic resource-resolution concerns.
 * • Allows both representations to travel together through the resolution
 *   pipeline without recomputing the Python string.
 *
 * Responsibilities
 * ----------------
 * • Preserve the source string required for PSI/editor operations.
 * • Preserve the evaluated string required for classification and resolution.
 * • Provide a stable boundary between Python string evaluation and resource
 *   handling.
 *
 * Value Semantics
 * ---------------
 *
 * +--------------------------------------+----------------------------------------------+
 * | Property                             | Purpose                                      |
 * +--------------------------------------+----------------------------------------------+
 * | sourceString                         | Source-oriented value used for PSI/editor     |
 * |                                      | correspondence and highlighting.             |
 * | resolvedString                       | Compile-time evaluated value used for         |
 * |                                      | classification and resource resolution.      |
 * +--------------------------------------+----------------------------------------------+
 *
 * Examples
 * --------
 *
 * +--------------------------------------+----------------------------------------------+
 * | Python Source                        | resolvedString                               |
 * +--------------------------------------+----------------------------------------------+
 * | 'docs/manual.pdf'                    | docs/manual.pdf                              |
 * | r'C:\Docs\manual.pdf'                | C:\Docs\manual.pdf                           |
 * | 'docs/' 'manual.pdf'                 | docs/manual.pdf                              |
 * | f'docs/{VERSION}.pdf'                | docs/v1.2.pdf                                |
 * +--------------------------------------+----------------------------------------------+
 *
 * The exact sourceString representation is determined by PythonStringResolver
 * and remains source-oriented so that IntelliJ PSI operations can retain
 * correspondence with the original Python literal.
 *
 * Data Flow
 * ---------
 *
 *     PyStringLiteralExpression
 *              |
 *              v
 *     PythonStringResolver
 *              |
 *              v
 *     PythonResolvedString
 *          /          \
 *         /            \
 *        v              v
 * sourceString      resolvedString
 *      |                 |
 *      v                 v
 * PSI/editor        ResourceClassifier
 * operations        ResourceResolver
 *
 * Architectural Notes
 * -------------------
 * • sourceString and resolvedString intentionally represent different concerns.
 *   They should not be treated as interchangeable merely because their values
 *   are identical for simple Python string literals.
 *
 * • The distinction becomes important for constructs such as adjacent literals,
 *   escaped strings, raw strings, formatted strings, and compile-time constant
 *   expressions where the physical source representation may differ from the
 *   evaluated Python value.
 *
 * • Keeping both representations together prevents downstream navigation code
 *   from discarding PSI/source information while still allowing resource logic
 *   to operate on the evaluated compile-time value.
 *
 * • This class contains no evaluation or resource-resolution logic. Construction
 *   is owned by PythonStringResolver; interpretation of resolvedString is owned
 *   by ResourceClassifier and ResourceResolver.
 *
 * See Also
 * --------
 * • PythonStringResolver
 * • PythonConstantStringEvaluator
 * • PythonStringUtil
 * • ResourceClassifier
 * • ResourceResolver
 *
 * Revision History
 * ----------------
 * v2.0.0 — 2026-07-23 (JS)
 * • Introduced PythonResolvedString to preserve both source-oriented and
 *   compile-time evaluated representations of Python strings.
 * • Separated PSI/editor string requirements from semantic resource-resolution
 *   requirements.
 * • Established the shared string representation used by PythonStringResolver,
 *   ResourceClassifier, and ResourceResolver.
 */
data class PythonResolvedString(

    /**
     * String exactly as represented by the Python source.
     *
     * Preserves adjacent literal boundaries and is intended for
     * editor/PSI operations requiring source correspondence.
     */
    val sourceString: String,

    /**
     * Compile-time evaluated string.
     *
     * Adjacent literals, raw strings, escaped characters,
     * constant references, etc. are resolved.
     */
    val resolvedString: String,
)