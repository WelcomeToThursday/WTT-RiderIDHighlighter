package com.wtt.rideridhighlighter

internal data class WttDefinition(val name: String, val kind: WttDefinitionKind) {
    fun displayName(id: String): String = if (name == id || kind.label == null) {
        name
    } else {
        "$name (${kind.label})"
    }
}
