package com.jschelert.resourcenavigator.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.config.ResourceNavigatorSettings
import com.jschelert.resourcenavigator.util.PythonResourceContext
import com.jschelert.resourcenavigator.util.PythonStringResolver
import com.jschelert.resourcenavigator.util.ResourceKind
import com.jschelert.resourcenavigator.util.ResourceResolver
import com.jschelert.resourcenavigator.util.ResourceClassifier

/**
 * =================================================================================================
 * MissingResourceInspection
 * =================================================================================================
 *
 * IDE Inspection
 * --------------
 * Reports unresolved local resource references recognized by Resource Navigator.
 *
 * Behavior
 * --------
 * • Visits Python string literals.
 * • Resolves each recognized resource using ResourceResolver.
 * • Reports missing local filesystem resources.
 * • URL resources and existing files are ignored.
 * • Inspection execution can be enabled or disabled through plugin settings.
 *
 * Responsibilities
 * ----------------
 * • Traverse Python string literals.
 * • Resolve candidate resources.
 * • Report unresolved local filesystem resources.
 *
 * Dependencies
 * ------------
 * • ResourceNavigatorSettings
 * • ResourceResolver
 * • ResourceKind
 * • IntelliJ LocalInspectionTool
 *
 * Inspection Behavior
 * -------------------
 *
 * +--------------------------------------+----------+---------------------------+
 * | Python String                        | Exists?  | Editor Behavior           |
 * +--------------------------------------+----------+---------------------------+
 * | "docs/manual.pdf"                    | Yes      | Hyperlink only            |
 * | "docs/missing.pdf"                   | No       | Hyperlink + Warning       |
 * | "https://jetbrains.com"              | N/A      | Hyperlink only            |
 * | "*.png"                              | N/A      | No hyperlink              |
 * | f"{filename}"                        | N/A      | No hyperlink              |
 * | f"docs/{VERSION}.pdf"                | Yes      | Tier 4: Hyperlink         |
 * | f"docs/{VERSION}.pdf"                | No       | Tier 4: Warning           |
 * +--------------------------------------+----------+---------------------------+
 *
 * See Also
 * --------
 * • ResourceResolver
 * • ResourceClassifier
 * • ResourceNavigationTargetFactory
 */
class MissingResourceInspection : LocalInspectionTool() {

    /**
     * Build the PSI visitor used to inspect Python string literals
     * for unresolved local resource references.
     */
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor {

        //
        // Skip inspection when disabled in plugin settings.
        //
        if (!ResourceNavigatorSettings.getInstance().state.warnOnMissingResources) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : PyElementVisitor() {

            /**
             * Inspect a Python string literal for unresolved local resources.
             */
            override fun visitPyStringLiteralExpression(
                node: PyStringLiteralExpression,
            ) {

                //
                // Determine the complete supported expression represented by this literal.
                //
                val expression =
                    PythonResourceContext.evaluationExpression(node)

                //
                // Resolve the complete compile-time expression.
                //
                val resolved =
                    PythonStringResolver.resolve(expression)
                        ?: return

                //
                // Determine whether this string should be inspected.
                //
                if (
                    !ResourceClassifier.shouldInspectMissing(
                        node,
                        resolved,
                    )
                ) {
                    return
                }

                //
                // Resolve the resource.
                //
                val target =
                    ResourceResolver.resolve(
                        node,
                        resolved,
                    ) ?: return

                //
                // Ignore existing resources and non-local files.
                //
                if (
                    target.kind != ResourceKind.LOCAL_FILE ||
                    target.exists
                ) {
                    return
                }

                //
                // Report the missing resource.
                //
                holder.registerProblem(
                    node,
                    "Referenced resource does not exist: ${
                        target.resolvedPath ?: target.sourceValue
                    }",
                )
            }
        }
    }
}