package com.wtt.rideridhighlighter

internal enum class WttDefinitionKind(val label: String?) {
    ITEM(null),
    ITEM_PARENT("Item Parent"),
    HANDBOOK("Handbook"),
    QUEST("Quest"),
    TRADER("Trader"),
    HEAD("Head"),
    VOICE("Voice"),
    CLOTHING("Clothing"),
    ACHIEVEMENT("Achievement"),
    PRESET("Preset"),
    RECIPE("Recipe"),
    CUSTOMIZATION("Customization"),
    HIDEOUT_CUSTOMIZATION("Hideout Customization"),
    CUSTOMIZATION_STORAGE("Customization Storage"),
    DIALOGUE("Dialogue")
}
