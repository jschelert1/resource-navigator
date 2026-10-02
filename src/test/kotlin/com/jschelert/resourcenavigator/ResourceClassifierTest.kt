package com.jschelert.resourcenavigator

import com.jschelert.resourcenavigator.config.ResourceNavigatorSettings
import com.jschelert.resourcenavigator.util.ResourceClassifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceClassifierTest {
    @Test fun recognizesHttpUrls() {
        assertTrue(ResourceClassifier.isUrl("https://example.com/test"))
        assertTrue(ResourceClassifier.isUrl("http://localhost:8000"))
        assertFalse(ResourceClassifier.isUrl("figures/test.svg"))
    }

    @Test fun extractsExtensions() {
        assertEquals("svg", ResourceClassifier.extension("figures/helix.svg"))
        assertEquals("pdf", ResourceClassifier.extension("paper.pdf#page=3"))
        assertEquals("py", ResourceClassifier.extension("C:\\Research\\plot.py"))
    }

    @Test fun recognizesPathShapes() {
        assertTrue(ResourceClassifier.looksLikeFilePath("figures/helix.svg"))
        assertTrue(ResourceClassifier.looksLikeFilePath("C:\\Research\\helix.svg"))
        assertTrue(ResourceClassifier.looksLikeFilePath("manual.pdf"))
        assertFalse(ResourceClassifier.looksLikeFilePath("ordinary_identifier"))
    }


    @Test fun conservativeMissingInspectionRejectsInferredResources() {
        val policy = ResourceNavigatorSettings.MissingResourcePolicy.CONSERVATIVE

        for (value in issue9AmbiguousFilenameValues + issue9StrongFilesystemPaths + listOf("/json/version"))
            assertFalse(ResourceClassifier.shouldInspectMissing(value, policy), value)
    }

    @Test fun balancedMissingInspectionRejectsAmbiguousFilenameValues() {
        val policy = ResourceNavigatorSettings.MissingResourcePolicy.BALANCED

        for (value in issue9AmbiguousFilenameValues)
            assertFalse(ResourceClassifier.shouldInspectMissing(value, policy), value)

        assertFalse(ResourceClassifier.shouldInspectMissing("/json/version", policy))
    }

    @Test fun balancedMissingInspectionAcceptsStrongFilesystemPaths() {
        val policy = ResourceNavigatorSettings.MissingResourcePolicy.BALANCED

        for (value in issue9StrongFilesystemPaths)
            assertTrue(ResourceClassifier.shouldInspectMissing(value, policy), value)
    }

    @Test fun aggressiveMissingInspectionPreservesBroadResourceInference() {
        val policy = ResourceNavigatorSettings.MissingResourcePolicy.AGGRESSIVE

        for (value in issue9AggressiveResourceValues)
            assertTrue(ResourceClassifier.shouldInspectMissing(value, policy), value)
    }

    @Test fun missingInspectionAlwaysRejectsKnownNonResources() {
        for (policy in ResourceNavigatorSettings.MissingResourcePolicy.entries)
            for (value in issue9KnownNonResources)
                assertFalse(ResourceClassifier.shouldInspectMissing(value, policy), "$policy: $value")
    }

    companion object {
        private val issue9AmbiguousFilenameValues =
            listOf(
                "cache.json",
                "chat_manager_profile.json",
                "chat_autoprompt.json",
                "cm_unified_cache.json",
                "sidebar_cache.json",
                "bookmarks.json",
                "_prompt_cache.json",
                "export_summary.xlsx",
                "chat_identity.json",
                "chat_decoded.html",
                "chat_normalized.txt",
                "Book contents V.3.0.xlsm",
                "chat_export_titles_example.xlsx",
                "pre_pdf.html",
            )

        private val issue9StrongFilesystemPaths =
            listOf(
                "docs/missing.pdf",
                "docs\\missing.pdf",
                "C:\\RNFixture\\missing.json",
                "\\\\server\\share\\missing.json",
                "/home/user/missing.json",
                "D:\\ChatExports\\missing.pdf",
                "C:\\Users\\example\\Desktop\\Exports\\missing.xlsx",
            )

        private val issue9AggressiveResourceValues =
            issue9AmbiguousFilenameValues +
                    issue9StrongFilesystemPaths +
                    listOf("/json/version")

        private val issue9KnownNonResources =
            listOf(
                "",
                "/F",
                "/IM",
                "\\",
                "\\\\",
                "\\n",
                "\\t",
                "\\{",
                "*.pdf",
                "*.json",
                ".gitignore",
                ".xlsx",
                ".csv",
                ".json.json",
                "{resource}.pdf",
            )
    }

}
