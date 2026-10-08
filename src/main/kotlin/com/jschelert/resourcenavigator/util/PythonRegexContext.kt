package com.jschelert.resourcenavigator.util

import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.intellij.psi.util.PsiTreeUtil

/**
 * Shared regex-pattern context detection for Issue #11.
 * Restrict suppression to the pattern argument of recognized re functions.
 */
object PythonRegexContext {
    private val patternFunctions = setOf(
        "compile", "match", "fullmatch", "search", "findall", "finditer",
        "split", "sub", "subn",
    )

    fun isRegexPattern(literal: PyStringLiteralExpression): Boolean {
        val call = PsiTreeUtil.getParentOfType(literal, PyCallExpression::class.java)
            ?: return false
        val callee = call.callee?.text ?: return false
        if (callee.substringAfterLast('.') !in patternFunctions) return false
        // Qualified re.* is unambiguous; bare names require import resolution
        // and are intentionally left untouched.
        if (callee.substringBeforeLast('.', "") != "re") return false
        val first = call.arguments.firstOrNull() ?: return false
        return PsiTreeUtil.isAncestor(first, literal, false)
    }
}
