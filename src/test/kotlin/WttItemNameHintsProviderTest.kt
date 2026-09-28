package com.wtt.rideridhighlighter

import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
import com.intellij.json.JsonLanguage
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase

class WttItemNameHintsProviderTest : DeclarativeInlayHintsProviderTestCase() {
    override fun setUp() {
        super.setUp()
        project.service<WttProjectSettings>().configure("", "en")
        waitForNames()
    }

    private fun waitForNames() {
        val settings = project.service<WttProjectSettings>()
        PlatformTestUtil.waitWithEventsDispatching({ settings.status }, { settings.status.startsWith("Loaded") }, 10)
    }

    fun testJsoncNamesAppearAtLineEndAfterCommasAndComments() {
        doTestProvider("items.jsonc", """
            {
              // "574d967124597745970e7c94" in a comment gets no hint.
              "itemTplToClone": "574d967124597745970e7c94",/*<# TOZ Simonov SKS 7.62x39 carbine #>*/
              "parentId": "5447b5fc4bdc2d87278b4567", // parent type/*<# Assault carbine #>*/
              "unknown": "000000000000000000000001",
              "text": "prefix 574d967124597745970e7c94",
            }
        """.trimIndent(), WttItemNameHintsProvider(), testMode = ProviderTestMode.SIMPLE)
    }

    fun testWttProjectNamesAppearOnRootAndCrossFileReferences() {
        val id = "0793d51e14f71b8c8f28e4f6"
        myFixture.addFileToProject("Resources/db/CustomItems/custom.jsonc", """
            {"$id": {
              "itemTplToClone":"574d967124597745970e7c94",
              "locales":{"en":{"name":"Sillyworks SKS-A"}},
            }}
        """.trimIndent())
        doTestProvider("references.jsonc", """
            {"_tpl":"$id"}/*<# Sillyworks SKS-A #>*/
        """.trimIndent(), WttItemNameHintsProvider(), testMode = ProviderTestMode.SIMPLE)
        doTestProvider("definition.jsonc", """
            {"$id": {/*<# Local SKS name #>*/
              "itemTplToClone":"574d967124597745970e7c94",/*<# TOZ Simonov SKS 7.62x39 carbine #>*/
              "locales":{"en":{"name":"Local SKS name"}}
            }}
        """.trimIndent(), WttItemNameHintsProvider(), testMode = ProviderTestMode.SIMPLE)
    }

    fun testJsonNamesForKeysAndArraysAndNoComma() {
        doTestProvider("items.json", """
            {
              "5448be9a4bdc2dfd2f8b456a": ["5448BE9A4BDC2DFD2F8B456A", "5448be9a4bdc2dfd2f8b456a"],/*<# RGD-5 hand grenade #>*//*<# RGD-5 hand grenade #>*//*<# RGD-5 hand grenade #>*/
              "_tpl": "5448be9a4bdc2dfd2f8b456a"/*<# RGD-5 hand grenade #>*/
            }
        """.trimIndent(), WttItemNameHintsProvider(), testMode = ProviderTestMode.SIMPLE)
    }

    fun testHandbookParentIdsShowCategoryHintsInJsonAndJsonc() {
        for (fileName in listOf("categories.json", "categories.jsonc")) {
            doTestProvider(fileName, """
                {
                  "handbookParentId": "5b5f78e986f77447ed5636b1",/*<# Assault carbines (Handbook) #>*/
                  "ParentId": "5b5f78fc86f77409407a7f90",/*<# Assault rifles (Handbook) #>*/
                  "untranslatedCategory": "6564b96a189fe36f356d177c"
                }
            """.trimIndent(), WttItemNameHintsProvider(), testMode = ProviderTestMode.SIMPLE)
        }
    }

    fun testUnknownInvalidAndEscapedStringsHaveNoHints() {
        doTestProvider("items.jsonc", """
            {
              /* "5448be9a4bdc2dfd2f8b456a" */
              "unknown": "000000000000000000000001",
              "escaped": "\u0035448be9a4bdc2dfd2f8b456a",
              "short": "5448be9a4bdc2dfd2f8b456",
              "long": "5448be9a4bdc2dfd2f8b456aa",
              "invalid": "g448be9a4bdc2dfd2f8b456a"
            }
        """.trimIndent(), WttItemNameHintsProvider(), testMode = ProviderTestMode.SIMPLE)
    }

    fun testNonItemNamesHaveTypeLabelsAndItemsRemainPlain() {
        doTestProvider("types.jsonc", """
            {
              "questId":"5936d90786f7742b1420ba5b",/*<# Debut (Quest) #>*/
              "traderId":"54cb50c76803fa8b248b4571",/*<# Prapor (Trader) #>*/
              "handbookParentId":"5b5f78e986f77447ed5636b1",/*<# Assault carbines (Handbook) #>*/
              "_tpl":"5448be9a4bdc2dfd2f8b456a"/*<# RGD-5 hand grenade #>*/
            }
        """.trimIndent(), WttItemNameHintsProvider(), testMode = ProviderTestMode.SIMPLE)
    }

    fun testProjectQuestNameAppearsInHints() {
        val id = "0793d51e14f71b8c8f28e4f6"
        myFixture.addFileToProject("db/CustomQuests/Prapor/Quests/quest.jsonc", """{"$id":{"name":"$id name","conditions":{},"rewards":{}}}""")
        myFixture.addFileToProject("db/CustomQuests/Prapor/Locales/en.json", """{"$id name":"My quest"}""")
        doTestProvider("references.jsonc", """{"quest":"$id"}/*<# My quest (Quest) #>*/""",
            WttItemNameHintsProvider(), testMode = ProviderTestMode.SIMPLE)
    }

    fun testDifferentIdsOnOneLineKeepTheirSourceOrderAfterClosingBracesAndComment() {
        doTestProvider("one-line.jsonc", """
            {"item":"5448be9a4bdc2dfd2f8b456a","quest":"5936d90786f7742b1420ba5b","category":"5b5f78e986f77447ed5636b1"} // all names follow/*<# RGD-5 hand grenade #>*//*<# Debut (Quest) #>*//*<# Assault carbines (Handbook) #>*/
        """.trimIndent(), WttItemNameHintsProvider(), testMode = ProviderTestMode.SIMPLE)
    }

    fun testProviderIsRegisteredAndEnabledByDefault() {
        val info = InlayHintsProviderFactory.getProviderInfo(JsonLanguage.INSTANCE, "spt.json.item.names")
        assertNotNull(info)
        assertTrue(info!!.isEnabledByDefault)
        assertTrue(info.provider is WttItemNameHintsProvider)
    }

    fun testChangingLocaleInvalidatesHintsWithoutEditingTheFile() {
        myFixture.configureByText("items.jsonc", """{"_tpl":"5448be9a4bdc2dfd2f8b456a"}""")
        DeclarativeInlayHintsPassFactory.updateModificationStamp(myFixture.editor, myFixture.file)
        val factory = DeclarativeInlayHintsPassFactory()
        assertNull(ActionUtil.underModalProgress(project, "Check cached hints") {
            factory.createHighlightingPass(myFixture.file, myFixture.editor)
        })
        project.service<WttProjectSettings>().configure("", "fr")
        waitForNames()
        assertNotNull(ActionUtil.underModalProgress(project, "Check refreshed hints") {
            factory.createHighlightingPass(myFixture.file, myFixture.editor)
        })
    }
}
