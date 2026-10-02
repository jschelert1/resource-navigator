package com.jschelert.resourcenavigator.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.config.ResourceNavigatorSettings
import com.jschelert.resourcenavigator.navigation.ResourceCitation
import com.jschelert.resourcenavigator.navigation.ResourceCitationParser
import com.jschelert.resourcenavigator.util.PythonResourceContext
import com.jschelert.resourcenavigator.util.PythonStringResolver
import com.jschelert.resourcenavigator.util.ResourceKind
import com.jschelert.resourcenavigator.util.ResourceResolver
import com.jschelert.resourcenavigator.util.ResourceClassifier
import com.jschelert.resourcenavigator.util.ResourceQuotedParser

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
 * • Gives embedded quoted resources precedence over whole-string inspection.
 * • Resolves each recognized resource using ResourceResolver.
 * • Reports missing local filesystem resources.
 * • URL resources and existing files are ignored.
 * • Inspection execution can be enabled or disabled through plugin settings.
 *
 * Responsibilities
 * ----------------
 * • Traverse Python string literals.
 * • Parse embedded quoted resources through ResourceQuotedParser.
 * • Resolve candidate resources.
 * • Report unresolved local filesystem resources.
 *
 * Dependencies
 * ------------
 * • ResourceNavigatorSettings
 * • ResourceResolver
 * • ResourceQuotedParser
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
                // Bracketed citations are explicit Resource Navigator syntax and take
                // precedence in every Python string context, including docstrings.
                //
                val citations =
                    ResourceCitationParser.parse(node)

                if (citations.isNotEmpty()) {
                    inspectCitationResources(
                        holder = holder,
                        node = node,
                        citations = citations,
                    )

                    return
                }

                //
                // Docstrings require explicit bracket syntax. Quoted and ordinary
                // path-like text remains documentation and must not produce warnings.
                //
                if (PythonResourceContext.isDocumentationString(node)) {
                    return
                }

                //
                // Give explicitly quoted resources precedence over whole-string
                // inspection so surrounding descriptive prose is never treated as
                // part of the filesystem path.
                //
                val quotedResources =
                    ResourceQuotedParser.parse(node)

                if (quotedResources.isNotEmpty()) {
                    inspectQuotedResources(
                        holder = holder,
                        node = node,
                        resources = quotedResources,
                    )

                    return
                }

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
    /**
     * Inspect explicit bracketed resource citations.
     *
     * Bracket syntax opts into Resource Navigator processing even inside Python
     * docstrings. Existing resources and URLs are ignored; missing local resources
     * are reported against the citation range.
     */
    private fun inspectCitationResources(
        holder: ProblemsHolder,
        node: PyStringLiteralExpression,
        citations: List<ResourceCitation>,
    ) {

        citations.forEach { citation ->

            val value =
                citation.text.trim()

            if (
                value.isEmpty() ||
                ResourceClassifier.isUrl(value) ||
                !ResourceClassifier.shouldHandle(
                    node,
                    value,
                )
            ) {
                return@forEach
            }

            val target =
                ResourceResolver.resolveLocal(
                    project = node.project,
                    containingFile = node.containingFile,
                    sourceValue = value,
                )

            if (target.exists) {
                return@forEach
            }

            holder.registerProblem(
                node,
                citation.range,
                "Referenced resource does not exist: ${
                    target.resolvedPath ?: target.sourceValue
                }",
            )
        }
    }

    /**
     * Inspect explicitly quoted resources embedded in descriptive Python text.
     *
     * Existing local resources and URLs are ignored. Missing local resources are
     * reported against each physical quoted-resource fragment rather than against
     * the complete descriptive Python string.
     */
    private fun inspectQuotedResources(
        holder: ProblemsHolder,
        node: PyStringLiteralExpression,
        resources: List<ResourceQuotedParser.QuotedResource>,
    ) {

        resources.forEach { resource ->

            val value =
                resource.text.trim()

            if (
                value.isEmpty() ||
                ResourceClassifier.isUrl(value) ||
                !ResourceClassifier.shouldHandle(
                    node,
                    value,
                )
            ) {
                return@forEach
            }

            val target =
                ResourceResolver.resolveLocal(
                    project = node.project,
                    containingFile = node.containingFile,
                    sourceValue = value,
                )

            if (target.exists) {
                return@forEach
            }

            resource.ranges.forEach { range ->
                holder.registerProblem(
                    node,
                    range,
                    "Referenced resource does not exist: ${
                        target.resolvedPath ?: target.sourceValue
                    }",
                )
            }
        }
    }
}