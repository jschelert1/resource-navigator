package com.jschelert.resourcenavigator

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.w3c.dom.Element
import java.util.ResourceBundle
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Regression tests for plugin descriptor metadata and localized action presentation.
 *
 * RH:
 * v1.0.0 — 2026-11-23
 * - Verify homepage, dependencies, localization declarations and registered actions.
 */
class PluginDescriptorTest : BasePlatformTestCase() {

    private fun descriptor(): Element =
        checkNotNull(javaClass.classLoader.getResourceAsStream("META-INF/plugin.xml"))
            .use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it).documentElement }

    private fun element(root: Element, name: String): Element {
        val nodes = root.getElementsByTagName(name)
        assertEquals("Expected exactly one " + name, 1, nodes.length)
        return nodes.item(0) as Element
    }

    private fun bundle(): ResourceBundle =
        ResourceBundle.getBundle("messages.ResourceBundle", Locale.ROOT, javaClass.classLoader)

    fun testHomepageAndDependencies() {
        val root = descriptor()
        assertEquals("idea-plugin", root.tagName)
        assertEquals("https://jschelert1.github.io/resource-navigator/", root.getAttribute("url"))
        assertEquals(0, root.getElementsByTagName("url").length)
        assertEquals("com.jschelert.resource-navigator", element(root, "id").textContent.trim())
        val nodes = root.getElementsByTagName("depends")
        val dependencies = (0 until nodes.length).map { nodes.item(it).textContent.trim() }
        assertTrue(dependencies.contains("com.intellij.modules.platform"))
        assertTrue(dependencies.contains("com.intellij.modules.python"))
    }

    fun testInspectionAndSettingsLocalization() {
        val root = descriptor()
        val properties = bundle()
        assertEquals("messages.ResourceBundle", element(root, "resource-bundle").textContent.trim())
        val inspection = element(root, "localInspection")
        assertEquals("Python", inspection.getAttribute("language"))
        assertEquals("MissingResource", inspection.getAttribute("shortName"))
        assertEquals("messages.ResourceBundle", inspection.getAttribute("bundle"))
        assertEquals("messages.ResourceBundle", inspection.getAttribute("groupBundle"))
        assertFalse(inspection.hasAttribute("displayName"))
        assertFalse(inspection.hasAttribute("groupName"))
        assertEquals("Unresolved resource reference", properties.getString(inspection.getAttribute("key")))
        assertEquals("Resource Navigator", properties.getString(inspection.getAttribute("groupKey")))
        val settings = element(root, "applicationConfigurable")
        assertEquals("messages.ResourceBundle", settings.getAttribute("bundle"))
        assertFalse(settings.hasAttribute("displayName"))
        assertEquals("Resource Navigator", properties.getString(settings.getAttribute("key")))
    }

    fun testActionLocalizationAndRuntimePresentation() {
        val actions = element(descriptor(), "actions")
        assertEquals("messages.ResourceBundle", actions.getAttribute("resource-bundle"))
        val expected = mapOf(
            "ResourceNavigator.OpenResource" to
                ("Open Resource" to "Open the resolved resource under the caret"),
            "ResourceNavigator.RevealResource" to
                ("Reveal Resource in File Manager" to "Reveal the resolved local resource under the caret"),
            "ResourceNavigator.CopyFullPath" to
                ("Copy Resource Full Path" to "Copy the resolved absolute resource path"),
        )
        val nodes = actions.getElementsByTagName("action")
        assertEquals(expected.size, nodes.length)
        val seen = mutableSetOf<String>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as Element
            val id = node.getAttribute("id")
            assertTrue("Duplicate action: " + id, seen.add(id))
            val (label, description) = checkNotNull(expected[id])
            assertFalse(node.hasAttribute("text"))
            assertFalse(node.hasAttribute("description"))
            assertEquals(label, bundle().getString("action." + id + ".text"))
            assertEquals(description, bundle().getString("action." + id + ".description"))
            val action = checkNotNull(ActionManager.getInstance().getAction(id)) {
                "Action not registered: " + id
            }
            assertEquals(label, action.templatePresentation.text)
            assertEquals(description, action.templatePresentation.description)
        }
        assertEquals(expected.keys, seen)
    }

    fun testUnsupportedLocalizationSyntaxIsAbsent() {
        val root = descriptor()
        val nodes = root.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val attributes = nodes.item(i).attributes
            for (j in 0 until attributes.length) {
                val attribute = attributes.item(j)
                assertFalse("Unsupported localization: " + attribute.nodeValue,
                    attribute.nodeValue.startsWith("%"))
            }
        }
    }
}
