package com.jschelert.resourcenavigator.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.config.ResourceNavigatorSettings
import com.jschelert.resourcenavigator.util.ResourceKind
import com.jschelert.resourcenavigator.util.ResourceResolver

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
 * See Also
 * --------
 * • ResourceResolver
 * • ResourceClassifier
 * • ResourceNavigationTargetFactory
 */
class MissingResourceInspection : LocalInspectionTool() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor {

        if (!ResourceNavigatorSettings.getInstance().state.warnOnMissingResources) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : PyElementVisitor() {

            override fun visitPyStringLiteralExpression(
                node: PyStringLiteralExpression,
            ) {

                val target =
                    ResourceResolver.resolve(node)
                        ?: return

                if (target.kind == ResourceKind.LOCAL_FILE && !target.exists) {

                    holder.registerProblem(
                        node,
                        "Referenced resource does not exist: ${
                            target.resolvedPath ?: target.rawValue
                        }",
                    )
                }
            }
        }
    }
}