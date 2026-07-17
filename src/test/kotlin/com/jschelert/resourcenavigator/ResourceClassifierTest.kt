package com.jschelert.resourcenavigator

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
}
