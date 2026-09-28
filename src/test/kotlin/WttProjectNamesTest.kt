@file:Suppress(
    "SpellCheckingInspection"
)

package com.wtt.rideridhighlighter

import com.wtt.rideridhighlighter.WttColorSettingsPage.WttColorSettingsPageObject.ID
import com.wtt.rideridhighlighter.WttColorSettingsPage.WttColorSettingsPageObject.ROOT_ID
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Suppress("SpellCheckingInspection", "SpellCheckingInspection")
class WttProjectNamesTest : BasePlatformTestCase() {
    private val id = "0793d51e14f71b8c8f28e4f6"
    private fun definition(name: String) = """{
        "$id": {
          "itemTplToClone": "574d967124597745970e7c94",
          "locales": {"en": {"name": "$name"}}
        }
    }""".trimIndent()

    private fun name(locale: String = "en"): String? = ActionUtil.underModalProgress(project, "Resolve project item") {
        ReadAction.compute<String?, RuntimeException> {
            project.service<WttProjectNames>().itemName(id, locale, myFixture.file)
        }
    }

    fun testFindsNamesInOtherJsonAndJsoncFilesWithLocaleFallback() {
        myFixture.addFileToProject(
            "Resources/db/CustomItems/custom.jsonc", """
            { // WTT JSONC definition
              "$id": {
                "itemTplToClone":"574d967124597745970e7c94",
                "locales": {
                  "en":{"name":"Custom SKS",},
                  "fr":{"name":"Carabine perso"},
                },
              },
            }
        """.trimIndent()
        )
        myFixture.configureByText("references.json", """{"_tpl":"$id"}""")
        assertEquals("Custom SKS", name())
        assertEquals("Carabine perso", name("fr"))
        assertEquals("Custom SKS", name("de"))
    }

    fun testCurrentUnsavedFileNameWinsAndInvalidStructuresAreIgnored() {
        myFixture.addFileToProject("other.json", definition("Other copy"))
        myFixture.configureByText("new.jsonc", definition("Current item"))
        assertEquals("Current item", name())
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.file.virtualFile.delete(this)
        }
        myFixture.configureByText("unrelated.json", """{"$id":{"locales":{"en":{"name":"Not a WTT item"}}}}""")
        assertEquals("Other copy", name())
        myFixture.configureByText("nested.json", """{"traders":${definition("Nested non-definition")}}""")
        assertEquals("Other copy", name())
    }

    fun testConflictingNamesAreNotChosenArbitrarilyAndMatchingDuplicatesAreFine() {
        myFixture.addFileToProject("first.json", definition("First"))
        myFixture.addFileToProject("second.json", definition("First"))
        myFixture.configureByText("references.jsonc", """{"_tpl":"$id"}""")
        assertEquals("First", name())
        myFixture.addFileToProject("third.json", definition("Different"))
        assertNull(name())
    }

    fun testCreatedChangedUnsavedAndDeletedDefinitionUpdatesOtherFileLookup() {
        myFixture.configureByText("references.jsonc", """{"_tpl":"$id"}""")
        assertNull(name())
        val definitionFile = myFixture.addFileToProject("new-item.json", definition("Before"))
        assertEquals("Before", name())
        WriteCommandAction.runWriteCommandAction(project) {
            val manager = PsiDocumentManager.getInstance(project)
            val document = manager.getDocument(definitionFile)!!
            document.replaceString(0, document.textLength, definition("After"))
            manager.commitAllDocuments()
        }
        assertEquals("After", name())
        WriteCommandAction.runWriteCommandAction(project) {
            definitionFile.virtualFile.delete(this)
        }
        assertNull(name())
    }

    fun testProjectDefinitionOverridesBundledNameInTooltip() {
        val knownId = "574d967124597745970e7c94"
        myFixture.addFileToProject("custom.json", definition("Project SKS").replace(id, knownId))
        myFixture.configureByText("reference.jsonc", """{"_tpl":"$knownId"}""")
        val info = myFixture.doHighlighting().single {
            it.forcedTextAttributesKey == ID
        }
        assertTrue(info.toolTip!!, info.toolTip!!.contains("Project SKS"))
    }

    fun testFirstDeclaredLocaleIsFallbackLikeCommonLib() {
        myFixture.addFileToProject("french.jsonc", definition("Nom français").replace("\"en\"", "\"fr\""))
        myFixture.configureByText("reference.json", """{"_tpl":"$id"}""")
        assertEquals("Nom français", name("en"))
    }

    fun testProjectQuestLocalesNeedDefinitionAndAreLabeled() {
        myFixture.addFileToProject("db/CustomQuests/Prapor/Locales/en.jsonc", """{"$id name":"A custom quest"}""")
        myFixture.configureByText("reference.jsonc", """{"questId":"$id"}""")
        assertNull("Locale keys alone must not create definitions", name())
        val quest = """{"$id":{"_id":"$id","name":"$id name","QuestName":"Internal quest","conditions":{},"rewards":{}}}"""
        myFixture.addFileToProject("db/CustomQuests/Prapor/Quests/custom.jsonc", quest)
        assertEquals("A custom quest (Quest)", name())
        myFixture.addFileToProject("db/CustomQuests/Prapor/Locales/fr.json", """{"$id name":"Une quête"}""")
        assertEquals("Une quête (Quest)", name("fr"))
        myFixture.configureByText("definition.json", quest)
        assertEmpty(myFixture.doHighlighting().filter { it.forcedTextAttributesKey == ROOT_ID })
    }

    fun testProjectTraderNamesAreLabeled() {
        myFixture.addFileToProject("Resources/data/base.json", """{"_id":"$id","nickname":"Milo","loyaltyLevels":[]}""")
        myFixture.configureByText("reference.jsonc", """{"traderId":"$id"}""")
        assertEquals("Milo (Trader)", name())
        myFixture.addFileToProject("Locales/en.json", """{"$id Nickname":"Milo the trader"}""")
        assertEquals("Milo the trader (Trader)", name())
    }

    fun testInstalledSillyworksDefinitionsWhenExplicitlySupplied() {
        val root = System.getenv("SPT_TEST_WTT_PROJECT") ?: return
        val source = java.nio.file.Path.of(root, "Sillyworks.Server/Resources/db/CustomItems/weapons/weapon_auto_sks.jsonc")
        val content = runBlocking(Dispatchers.IO) {
            val eel = source.getEelDescriptor().toEelApi()
            val file = eel.fs.readFile(object : EelFileSystemApi.ReadFileArgs {
                override val path = source.asEelPath()
            }).getOrThrow()
            Charsets.UTF_8.decode(file.bytes).toString()
        }
        myFixture.addFileToProject("Resources/db/CustomItems/weapon_auto_sks.jsonc", content)
        myFixture.configureByText("reference.jsonc", """{"_tpl":"$id"}""")
        assertEquals("Sillyworks SKS-A 7.62x39 automatic carbine", name())
    }

    fun testRepeatedReferencesReuseResolvedObjectAndKeepLocalesSeparate() {
        myFixture.addFileToProject("definition.json", definition("English").replace("\"en\": {\"name\": \"English\"}", "\"en\": {\"name\": \"English\"}, \"fr\": {\"name\": \"Français\"}"))
        myFixture.configureByText("reference.jsonc", """{"_tpl":"$id"}""")
        ActionUtil.underModalProgress(project, "Verify result reuse") {
            ReadAction.run<RuntimeException> {
                val resolver = project.service<WttProjectNames>()
                val english = resolver.definition(id, "en", myFixture.file)
                val french = resolver.definition(id, "fr", myFixture.file)
                assertEquals("English", english!!.name)
                assertEquals("Français", french!!.name)
                repeat(1000) {
                    assertSame(english, resolver.definition(id, "en", myFixture.file))
                    assertSame(french, resolver.definition(id, "fr", myFixture.file))
                }
            }
        }
    }

    fun testLocalOverridesRemainSeparateEvenWhenOnlyLocaleOrderDiffers() {
        val first = myFixture.addFileToProject(
            "first.json", definition("English").replace(
                "\"en\": {\"name\": \"English\"}", "\"en\": {\"name\": \"English\"}, \"fr\": {\"name\": \"Français\"}"
            )
        )
        val second = myFixture.addFileToProject(
            "second.json", definition("English").replace(
                "\"en\": {\"name\": \"English\"}", "\"fr\": {\"name\": \"Français\"}, \"en\": {\"name\": \"English\"}"
            )
        )
        ActionUtil.underModalProgress(project, "Verify local overrides") {
            ReadAction.run<RuntimeException> {
                val resolver = project.service<WttProjectNames>()
                repeat(3) {
                    assertEquals("English", resolver.itemName(id, "de", first))
                    assertEquals("Français", resolver.itemName(id, "de", second))
                }
            }
        }
    }

    fun testQuestLocaleEditsInvalidateResolvedNamesImmediately() {
        myFixture.addFileToProject("quests.json", """{"$id":{"name":"$id name","conditions":{},"rewards":{}}}""")
        val locale = myFixture.addFileToProject("Locales/en.jsonc", """{"$id name":"Before"}""")
        myFixture.configureByText("reference.jsonc", """{"quest":"$id"}""")
        assertEquals("Before (Quest)", name())
        assertEquals("Before (Quest)", name())
        WriteCommandAction.runWriteCommandAction(project) {
            val manager = PsiDocumentManager.getInstance(project)
            val document = manager.getDocument(locale)!!
            document.replaceString(0, document.textLength, """{"$id name":"After"}""")
            manager.commitAllDocuments()
        }
        assertEquals("After (Quest)", name())
    }

    fun testRenamingDefinitionOutOfJsonAndBackInvalidatesHitsAndMisses() {
        val source = myFixture.addFileToProject("custom.json", definition("Custom"))
        myFixture.configureByText("reference.jsonc", """{"_tpl":"$id"}""")
        assertEquals("Custom", name())
        val virtualFile = source.virtualFile
        WriteCommandAction.runWriteCommandAction(project) { virtualFile.rename(this, "custom.txt") }
        assertNull(name())
        WriteCommandAction.runWriteCommandAction(project) { virtualFile.rename(this, "custom.jsonc") }
        assertEquals("Custom", name())
    }

    fun testExplicitIndexRefreshCoalescesAndDoesNotLoopWhenHintsRefresh() {
        myFixture.addFileToProject("custom.json", definition("Custom"))
        myFixture.configureByText("reference.jsonc", """{"_tpl":"$id"}""")
        val resolver = project.service<WttProjectNames>()
        assertEquals("Custom", name())
        val before = resolver.completedIndexRefreshes
        resolver.refreshFromIndex()
        resolver.refreshFromIndex()
        val last = resolver.refreshFromIndex()
        PlatformTestUtil.waitWithEventsDispatching("Refresh index", { last.isCompleted }, 10)
        assertFalse("Index refresh must finish successfully", last.isCancelled)
        assertEquals(before + 1, resolver.completedIndexRefreshes)
        assertEquals("Custom", name())
        project.service<WttProjectSettings>().refreshEditorHints()
        val deadline = System.nanoTime() + 800_000_000L
        PlatformTestUtil.waitWithEventsDispatching("Allow deferred hint events", { System.nanoTime() >= deadline }, 3)
        assertEquals(
            "Hint updates must not schedule another index refresh",
            before + 1,
            resolver.completedIndexRefreshes
        )
    }
}
