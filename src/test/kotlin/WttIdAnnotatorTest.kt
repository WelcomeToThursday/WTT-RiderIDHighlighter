package com.wtt.rideridhighlighter

import com.wtt.rideridhighlighter.WttColorSettingsPage.WttColorSettingsPageObject.HANDBOOK_ID
import com.wtt.rideridhighlighter.WttColorSettingsPage.WttColorSettingsPageObject.ID
import com.wtt.rideridhighlighter.WttColorSettingsPage.WttColorSettingsPageObject.ROOT_ID
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.testFramework.PlatformTestUtil
import java.awt.Color
import java.awt.Font
import java.nio.file.Files

class WttIdAnnotatorTest : BasePlatformTestCase() {
    fun testBundledSchemesKeepIdColorsAndTextStyles() {
        val schemes = EditorColorsManager.getInstance()
        for (schemeName in listOf("Default", "Darcula")) {
            val scheme = schemes.getScheme(schemeName)
            assertNotNull("Missing $schemeName colour scheme", scheme)
            for ((key, rgb) in listOf(ROOT_ID to 0x7B78C8, ID to 0x8D9C6A, HANDBOOK_ID to 0x79B5A3)) {
                val attributes = scheme!!.getAttributes(key)
                assertNotNull("Missing $key in $schemeName", attributes)
                assertEquals(Color(rgb), attributes!!.foregroundColor)
                assertEquals(Color(rgb), attributes.effectColor)
                assertEquals(EffectType.LINE_UNDERSCORE, attributes.effectType)
                assertEquals(Font.ITALIC, attributes.fontType)
                assertNull(attributes.backgroundColor)
            }
        }
    }

    fun testCustomRootAndReferencesHaveDistinctStylesInJsonAndJsonc() {
        val settings = project.service<WttProjectSettings>()
        settings.configure("", "en")
        PlatformTestUtil.waitWithEventsDispatching("Load names", { settings.status.startsWith("Loaded") }, 10)
        val rootId = "0793d51e14f71b8c8f28e4f6"
        val itemId = "574d967124597745970e7c94"
        val categoryId = "5b5f78e986f77447ed5636b1"
        val styleKeys = setOf(ROOT_ID, ID, HANDBOOK_ID)
        for (extension in listOf("json", "jsonc")) {
            myFixture.configureByText("definitions.$extension", """
                {"$rootId": {
                  "itemTplToClone": "$itemId",
                  "parentId": "5447b5fc4bdc2d87278b4567",
                  "handbookParentId": "$categoryId",
                  "references": ["$rootId", "${categoryId.uppercase()}"]
                }}
            """.trimIndent())
            val highlights = myFixture.doHighlighting().filter { it.forcedTextAttributesKey in styleKeys }
            assertEquals(listOf(
                ROOT_ID, ID, ID,
                HANDBOOK_ID, ID, HANDBOOK_ID
            ),
                highlights.map { it.forcedTextAttributesKey })
            assertEquals(rootId, myFixture.file.text.substring(highlights.first().startOffset, highlights.first().endOffset))
        }
    }

    fun testNestedDefinitionIsRootButOrdinaryNestedIdKeyIsNot() {
        myFixture.configureByText("wrapped.jsonc", """
            {"definitions": {
              "0793d51e14f71b8c8f28e4f6": {"itemTplToClone":"574d967124597745970e7c94"},
              "0793d51e14f71b8c8f28e4f7": {"count":1}
            }}
        """.trimIndent())
        val roots = myFixture.doHighlighting().filter { it.forcedTextAttributesKey == ROOT_ID }
        assertEquals(1, roots.size)
        assertEquals("0793d51e14f71b8c8f28e4f6", myFixture.file.text.substring(roots.single().startOffset, roots.single().endOffset))
    }

    fun testLocalCategoriesRefreshClassificationWithoutChangingJson() {
        val categoryId = "0793d51e14f71b8c8f28e4f6"
        val root = Files.createTempDirectory("spt-category-style-")
        val settings = project.service<WttProjectSettings>()
        try {
            Files.createDirectories(root.resolve("templates"))
            Files.createDirectories(root.resolve("locales/global"))
            Files.writeString(root.resolve("templates/items.json"), "{}")
            Files.writeString(root.resolve("templates/handbook.json"), """{"Categories":[{"Id":"$categoryId"}]}""")
            Files.writeString(root.resolve("locales/global/en.json"), """{"$categoryId":"Custom category"}""")
            settings.configure(root.toString(), "en")
            PlatformTestUtil.waitWithEventsDispatching("Load local category", { settings.status.startsWith("Loaded") }, 10)
            myFixture.configureByText("custom.jsonc", """{"handbookParentId":"$categoryId"}""")
            assertEquals(1, myFixture.doHighlighting().count { it.forcedTextAttributesKey == HANDBOOK_ID })
            settings.configure("", "en")
            PlatformTestUtil.waitWithEventsDispatching("Load bundled categories", { settings.status.startsWith("Loaded") }, 10)
            assertEquals(0, myFixture.doHighlighting().count { it.forcedTextAttributesKey == HANDBOOK_ID })
            assertEquals(1, myFixture.doHighlighting().count { it.forcedTextAttributesKey == ID })
        } finally {
            settings.configure("", "en")
            root.toFile().deleteRecursively()
        }
    }

    fun testHandbookCategoryIsHighlightedWithNameTooltip() {
        val settings = project.service<WttProjectSettings>()
        settings.configure("", "en")
        PlatformTestUtil.waitWithEventsDispatching("Load names", { settings.status.startsWith("Loaded") }, 10)
        myFixture.configureByText("category.jsonc", """{"handbookParentId":"5b5f78e986f77447ed5636b1"}""")
        val highlight = myFixture.doHighlighting().single { it.forcedTextAttributesKey == HANDBOOK_ID }
        assertEquals("5b5f78e986f77447ed5636b1", myFixture.file.text.substring(highlight.startOffset, highlight.endOffset))
        assertTrue(highlight.toolTip!!.contains("Assault carbines"))
    }

    fun testFreshProjectUsesBundledNamesWithoutConfiguration() {
        // Initial background loading intentionally refreshes the highlighting pass.
        (myFixture as com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl)
            .canChangeDocumentDuringHighlighting(true)
        val settings = project.service<WttProjectSettings>()
        settings.loadState(WttProjectSettings.Options())
        myFixture.configureByText("bundled.jsonc", """{/* no database setup */ "_tpl":"5448be9a4bdc2dfd2f8b456a"}""")
        myFixture.doHighlighting()
        PlatformTestUtil.waitWithEventsDispatching({ "Load bundled names: ${settings.status}" }, { settings.status.startsWith("Loaded") }, 10)
        val highlight = myFixture.doHighlighting().single { it.forcedTextAttributesKey == ID }
        assertTrue(highlight.toolTip!!, highlight.toolTip!!.contains("RGD-5 hand grenade"))
        assertTrue(settings.status.contains("bundled"))
    }

    fun testLoadsNamesEscapesTooltipAndClearsOldNamesWhenDatabaseChanges() {
        checkNamesAndDatabaseChanges("item.json")
    }

    fun testJsoncLoadsNamesEscapesTooltipAndClearsOldNamesWhenDatabaseChanges() {
        checkNamesAndDatabaseChanges("item.jsonc")
    }

    private fun checkNamesAndDatabaseChanges(fileName: String) {
        val id = "5448be9a4bdc2dfd2f8b456a"
        val root = Files.createTempDirectory("spt-id-highlighter-test-")
        Files.createDirectories(root.resolve("templates"))
        Files.createDirectories(root.resolve("locales/global"))
        Files.writeString(root.resolve("templates/items.json"), """{"$id":{"_name":"internal"}}""")
        Files.writeString(root.resolve("locales/global/en.json"), """{"$id Name":"Food <special> & tasty"}""")
        val settings = project.service<WttProjectSettings>()
        try {
            settings.configure(root.toString(), "en")
            PlatformTestUtil.waitWithEventsDispatching("Load SPT database", { !settings.status.startsWith("Loading") }, 10)
            assertTrue(settings.status, settings.status.startsWith("Loaded 1"))
            val content = if (fileName.endsWith(".jsonc")) """{/* item template */ "_tpl":"$id",}"""
                else """{"_tpl":"$id"}"""
            myFixture.configureByText(fileName, content)
            val highlight = myFixture.doHighlighting().single { it.forcedTextAttributesKey == ID }
            assertTrue(highlight.toolTip!!, highlight.toolTip!!.contains("Food &lt;special&gt; &amp; tasty"))
            settings.configure("", "en")
            PlatformTestUtil.waitWithEventsDispatching("Restore bundled names", { settings.status.startsWith("Loaded") }, 10)
            val cleared = myFixture.doHighlighting().single { it.forcedTextAttributesKey == ID }
            assertFalse(cleared.toolTip!!.contains("Food"))
            assertTrue(cleared.toolTip!!.contains("RGD-5 hand grenade"))
        } finally {
            settings.configure("", "en")
            root.toFile().deleteRecursively()
        }
    }

    fun testJsoncHighlightsKeysValuesAndArraysButNotComments() {
        val id = "5448be9a4bdc2dfd2f8b456a"
        myFixture.configureByText("items.jsonc", """
            {
              // "$id" is only a comment, so it must not be highlighted.
              "$id": "$id",
              /* "$id" must not be highlighted either. */
              "items": ["${id.uppercase()}",],
              "text": "prefix $id",
              "invalid": "g${id.drop(1)}",
            }
        """.trimIndent())
        assertTrue("The .jsonc extension must select the JSON parser", myFixture.file is com.intellij.json.psi.JsonFile)
        val highlights = myFixture.doHighlighting().filter { it.forcedTextAttributesKey == ID }
        assertEquals(3, highlights.size)
        assertEquals(listOf(id, id, id.uppercase()), highlights.map { myFixture.file.text.substring(it.startOffset, it.endOffset) })
        assertTrue(highlights.all { it.toolTip?.contains("SPT") == true })
    }

    fun testHighlightsKeysValuesAndArrayEntriesWithoutQuotes() {
        val id = "5448be9a4bdc2dfd2f8b456a"
        myFixture.configureByText("items.json", """{"$id":"$id","items":["${id.uppercase()}"],"text":"prefix $id","short":"${id.drop(1)}","long":"${id}a","invalid":"g${id.drop(1)}"}""")
        val highlights = myFixture.doHighlighting().filter { it.forcedTextAttributesKey == ID }
        assertEquals(3, highlights.size)
        assertEquals(listOf(id, id, id.uppercase()), highlights.map { myFixture.file.text.substring(it.startOffset, it.endOffset) })
        assertTrue(highlights.all { it.toolTip?.contains("SPT") == true })
    }

    fun testDoesNotHighlightEscapedOrUnterminatedStrings() {
        myFixture.configureByText("items.json", """{"escaped":"\u0035448be9a4bdc2dfd2f8b456a","unfinished":"5448be9a4bdc2dfd2f8b456a}""")
        assertEmpty(myFixture.doHighlighting().filter { it.forcedTextAttributesKey == ID })
    }

    fun testDoesNotHighlightOtherFileTypes() {
        myFixture.configureByText("notes.txt", "\"5448be9a4bdc2dfd2f8b456a\"")
        assertEmpty(myFixture.doHighlighting().filter { it.forcedTextAttributesKey == ID })
    }
}
