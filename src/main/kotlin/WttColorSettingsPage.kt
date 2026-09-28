package com.wtt.rideridhighlighter

import com.intellij.json.highlighting.JsonSyntaxHighlighterFactory
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage

class WttColorSettingsPage : ColorSettingsPage {
    object WttColorSettingsPageObject {
        val ROOT_ID: TextAttributesKey = TextAttributesKey.createTextAttributesKey("SPT_ROOT_ITEM_ID", DefaultLanguageHighlighterColors.IDENTIFIER)
        val ID: TextAttributesKey = TextAttributesKey.createTextAttributesKey("SPT_ITEM_ID", DefaultLanguageHighlighterColors.IDENTIFIER)
        val HANDBOOK_ID: TextAttributesKey = TextAttributesKey.createTextAttributesKey("SPT_HANDBOOK_ID", DefaultLanguageHighlighterColors.IDENTIFIER)
    }

    override fun getDisplayName() = "WTT IDs"
    override fun getIcon() = null
    override fun getHighlighter() = JsonSyntaxHighlighterFactory().getSyntaxHighlighter(null, null)
    override fun getDemoText() = """
        {
          "<rootId>0793d51e14f71b8c8f28e4f6</rootId>": {
            "itemTplToClone": "<sptId>574d967124597745970e7c94</sptId>",
            "parentId": "<sptId>5447b5fc4bdc2d87278b4567</sptId>",
            "handbookParentId": "<handbookId>5b5f78e986f77447ed5636b1</handbookId>",
            "description": "Ordinary strings keep their JSON color"
          }
        }
    """.trimIndent()
    override fun getAdditionalHighlightingTagToDescriptorMap() = mapOf(
        "sptId" to WttColorSettingsPageObject.ID,
        "rootId" to WttColorSettingsPageObject.ROOT_ID,
        "handbookId" to WttColorSettingsPageObject.HANDBOOK_ID,
    )
    override fun getAttributeDescriptors() = arrayOf(
        AttributesDescriptor("Root item ID", WttColorSettingsPageObject.ROOT_ID),
        AttributesDescriptor("Item reference", WttColorSettingsPageObject.ID),
        AttributesDescriptor("Handbook category ID", WttColorSettingsPageObject.HANDBOOK_ID),
    )
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
}

