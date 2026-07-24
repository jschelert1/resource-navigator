package com.jschelert.resourcenavigator.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jschelert.resourcenavigator.config.ResourceNavigatorSettings
import com.jschelert.resourcenavigator.util.PythonResolvedString
import com.jschelert.resourcenavigator.util.PythonResourceContext
import com.jschelert.resourcenavigator.util.PythonStringResolver
import com.jschelert.resourcenavigator.util.ResourceKind
import com.jschelert.resourcenavigator.util.ResourceResolver

/**
 * =================================================================================================
 * ResourceGotoDeclarationHandler
 * =================================================================================================
 *
 * Go To Declaration Handler
 * -------------------------
 * Implements Ctrl+Click and Go to Declaration navigation for resources
 * referenced from Python string literals.
 *
 * Behavior
 * --------
 * • Locates the enclosing Python string literal beneath the caret.
 * • Gives precedence to bracketed resource citations.
 * • Falls back to ordinary one-resource-per-string navigation.
 * • Evaluates supported compile-time resource expressions.
 * • Preserves the original string literal as the navigation source.
 * • Resolves resources before creating navigation targets.
 * • Prevents navigation to missing local resources.
 *
 * Responsibilities
 * ----------------
 * • Integrate Resource Navigator with the IntelliJ Go to Declaration API.
 * • Dispatch between citation and ordinary resource navigation.
 * • Select the appropriate compile-time resource evaluation scope.
 * • Preserve IntelliJ/PyCharm navigation ownership of surrounding expressions.
 * • Delegate string evaluation, resource resolution, and target creation.
 *
 * Dependencies
 * ------------
 * • PythonResourceContext
 * • PythonStringResolver
 * • ResourceCitationParser
 * • ResourceResolver
 * • ResourceNavigationTargetFactory
 * • ResourceKind
 * • PyStringLiteralExpression
 * • PsiTreeUtil
 *
 * See Also
 * --------
 * • ResourceReferenceContributor
 * • ResourceNavigationTargetFactory
 * • ResourceDispatcher
 * • ResourceReferenceLocal
 * • ResourceReferenceUrl
 *
 * Architectural Notes
 * -------------------
 * • Bracketed citations intentionally take precedence over ordinary resource
 *   literals so that multiple independently navigable resources may coexist
 *   within a single Python string.
 *
 * • Resource evaluation scope and navigation ownership are intentionally
 *   separate. Tier 4 expressions may be evaluated beyond the literal beneath
 *   the caret, while the original literal remains the Resource Navigator
 *   navigation source.
 *
 * • Surrounding Python references such as BASE_PATH retain native PyCharm
 *   declaration navigation and are never claimed as Resource Navigator links.
 *
 * • Resource resolution and target creation are delegated to their respective
 *   subsystems. This class coordinates navigation flow rather than performing
 *   resource parsing or resolution itself.
 */
class ResourceGotoDeclarationHandler : GotoDeclarationHandler {

    /**
     * Return the navigation targets for the resource beneath the caret.
     *
     * Behavior
     * --------
     * • Attempts bracketed citation navigation first.
     * • Falls back to ordinary resource navigation.
     * • Evaluates supported Tier 4 compile-time expressions while retaining the
     *   original string literal as the navigation source.
     * • Returns null when no valid navigation target exists.
     *
     * Resolution Flow
     * ---------------
     *
     *                         Python string literal
     *                                  |
     *                                  v
     *                    evaluationExpression(literal)
     *                                  |
     *                    +-------------+-------------+
     *                    |                           |
     *           expression === literal     expression !== literal
     *                    |                           |
     *                    v                           v
     *               Tier 1–3                     Tier 4
     *                    |                           |
     *                    v                           v
     *       resolve(literal)              resolve(expression)
     *                    |                           |
     *                    v                           v
     *       ResourceResolver              ResourceResolver
     *         .resolve(literal)             .resolve(literal, resolved)
     *                    |                           |
     *                    +-------------+-------------+
     *                                  |
     *                                  v
     *                         navigation target
     *
     * Tier Examples
     * -------------
     *
     * Tier 1 — ordinary literal:
     *
     *     path = r"C:\docs\paper.pdf"
     *
     * Tier 2 — adjacent literal concatenation:
     *
     *     path = (
     *         r"C:\docs"
     *         r"\paper.pdf"
     *     )
     *
     * Tier 3 — constant f-string:
     *
     *     path = f"C:\docs\{{archive}}\paper.pdf"
     *
     * Tier 4 — compile-time expression:
     *
     *     BASE_PATH = r"C:\docs"
     *     path = BASE_PATH + r"\paper.pdf"
     *
     * For Tier 4, only the trailing resource literal is owned by Resource
     * Navigator. References such as BASE_PATH retain native PyCharm declaration
     * navigation.
     *
     * Implementation Note
     * -------------------
     * Ordinary Tier 1–3 resources are intentionally resolved from the original
     * PyStringLiteralExpression rather than from the reconstructed
     * PythonResolvedString.
     *
     * Although PythonStringResolver correctly reconstructs adjacent string
     * literals, passing that resolved value through
     * ResourceResolver.resolve(literal, resolved) causes IntelliJ Ctrl-hover
     * highlighting/navigation to fragment or fail for multiline and adjacent
     * Python string literals.
     *
     * Tier 4 expressions require the pre-resolved path because their complete
     * resource value may depend on surrounding compile-time expressions. The
     * original literal is nevertheless retained as the navigation source so
     * Resource Navigator does not interfere with native PyCharm navigation on
     * referenced Python variables.
     *
     * ResourceResolver.resolve(literal, resolved) may be temporarily used for
     * ordinary literals when diagnosing the IntelliJ multiline-highlighting
     * behavior.
     */
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor,
    ): Array<PsiElement>? {

        //
        // Locate the enclosing Python string literal PSI element.
        //
        val literal =
            findStringLiteral(sourceElement)
                ?: return null

        //
        // Emit caret-to-literal diagnostics when global diagnostics are enabled.
        //
        if (ResourceNavigatorSettings.DIAGNOSTICS_ENABLED) {
            diagnoseLiteral(
                sourceElement,
                literal,
                offset,
            )
        }

        /*
         * --------------------------------------------------
         * Bracket citation
         * --------------------------------------------------
         */

        //
        // Locate the bracketed resource citation beneath the caret.
        //
        ResourceCitationParser.findCitation(
            literal,
            offset,
        )?.let { citation ->

            //
            // Resolve the citation as a local resource.
            //
            val target =
                ResourceResolver.resolveLocal(
                    project = literal.project,
                    containingFile = literal.containingFile,
                    sourceValue = citation.text,
                )

            //
            // Reject missing local resources.
            //
            if (
                target.kind == ResourceKind.LOCAL_FILE &&
                !target.exists
            ) {
                return null
            }

            //
            // Create and return the IntelliJ navigation target.
            //
            return ResourceNavigationTargetFactory.createTargets(
                source = literal,
                target = target,
            )
        }

        /*
         * --------------------------------------------------
         * Ordinary resource literal
         * --------------------------------------------------
         */

        //
        // Select the expression scope used for compile-time resource evaluation.
        //
        val expression =
            PythonResourceContext.evaluationExpression(literal)

        //
        // Resolve the Python literal into its source and evaluated string values.
        //
        val resolved =
            if (expression === literal) {

                //
                // Tier 1–3 / ordinary literal.
                //
                PythonStringResolver.resolve(literal)

            } else {

                //
                // Tier 4 compile-time expression.
                //
                PythonStringResolver.resolve(expression)

            } ?: return null

        //
        // Resolve the evaluated expression into its navigable resource target.
        //
        val target =
            if (expression === literal) {

                //
                // Tier 1–3 / ordinary literal.
                //
                // Preserve the original literal-based resolution path because this
                // maintains IntelliJ adjacent/multiline highlighting behavior.
                //
                ResourceResolver.resolve(
                    literal,
                )

            } else {

                //
                // Tier 4 compile-time expression.
                //
                // Evaluate the enclosing expression to obtain the complete resource
                // while retaining the literal as the navigation source.
                //
                ResourceResolver.resolve(
                    literal,
                    resolved,
                )
            } ?: return null

        //
        // Emit resource-resolution diagnostics when global diagnostics are enabled.
        //
        if (ResourceNavigatorSettings.DIAGNOSTICS_ENABLED) {
            diagnoseResolver(
                literal,
                resolved,
            )
        }

        //
        // Reject missing local resources.
        //
        if (
            target.kind == ResourceKind.LOCAL_FILE &&
            !target.exists
        ) {
            return null
        }

        //
        // Create and return the IntelliJ navigation target.
        //
        return ResourceNavigationTargetFactory.createTargets(
            source = literal,
            target = target,
        )
    }

    /**
     * Return the action text displayed by the IDE.
     */
    override fun getActionText(
        context: DataContext,
    ): String =
        "Open Resource"

    /**
     * Locate the Python string literal associated with the supplied PSI
     * element.
     */
    private fun findStringLiteral(
        sourceElement: PsiElement?,
    ): PyStringLiteralExpression? {

        sourceElement ?: return null

        return sourceElement as? PyStringLiteralExpression
            ?: PsiTreeUtil.getParentOfType(
                sourceElement,
                PyStringLiteralExpression::class.java,
                false,
            )
    }

    /**
     * Print diagnostic information about the PSI source element and
     * enclosing Python string literal.
     *
     * Intended for debugging IntelliJ caret-to-literal resolution and
     * multiline string navigation behavior.
     */
    private fun diagnoseLiteral(
        sourceElement: PsiElement?,
        literal: PyStringLiteralExpression,
        offset: Int,
    ) {

        println()
        println("===== GotoDeclarationHandler =====")
        println("offset = $offset")
        println("source = ${sourceElement?.text}")
        println("sourceElement class = ${sourceElement?.javaClass}")
        println("literal class       = ${literal.javaClass}")
        println("source == literal   = ${sourceElement == literal}")
        println("source text         = ${sourceElement?.text}")
        println("literal text        = ${literal.text}")
    }

    /**
     * Compare literal-based and pre-resolved resource resolution.
     *
     * Intended for diagnosing differences between the legacy literal
     * resolution path and the newer PythonStringResolver pipeline,
     * particularly IntelliJ multiline highlighting behavior.
     */
    private fun diagnoseResolver(
        literal: PyStringLiteralExpression,
        resolved: PythonResolvedString,
    ) {

        //
        // Resolve through both resolution paths.
        //
        val oldTarget =
            ResourceResolver.resolve(literal)

        val newTarget =
            ResourceResolver.resolve(
                literal,
                resolved,
            )

        //
        // Print the comparison.
        //
        println()
        println("===== Resolver Comparison =====")
        println("literal text     = <${literal.text}>")
        println("source string    = <${resolved.sourceString}>")
        println("resolved string  = <${resolved.resolvedString}>")
        println("oldTarget        = $oldTarget")
        println("newTarget        = $newTarget")
        println("old is null      = ${oldTarget == null}")
        println("new is null      = ${newTarget == null}")

        if (oldTarget != null) {
            println("old kind         = ${oldTarget.kind}")
            println("old exists       = ${oldTarget.exists}")
            println("old file         = ${oldTarget.virtualFile}")
            println("old source       = <${oldTarget.sourceValue}>")
            println("old resolved     = <${oldTarget.resolvedValue}>")
        }

        if (newTarget != null) {
            println("new kind         = ${newTarget.kind}")
            println("new exists       = ${newTarget.exists}")
            println("new file         = ${newTarget.virtualFile}")
            println("new source       = <${newTarget.sourceValue}>")
            println("new resolved     = <${newTarget.resolvedValue}>")
        }

        if (
            oldTarget != null &&
            newTarget != null
        ) {
            println("same instance    = ${oldTarget === newTarget}")
            println("equal            = ${oldTarget == newTarget}")
        }
    }
}