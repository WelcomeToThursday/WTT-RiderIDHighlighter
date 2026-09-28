package com.wtt.rideridhighlighter

import com.intellij.json.psi.JsonStringLiteral
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.psi.PsiElement
import java.util.Locale

internal object WttId {
    fun isRootItemId(element: PsiElement): Boolean {
        val property = element.parent as? JsonProperty ?: return false
        if (property.nameElement != element) return false
        val definition = property.value as? JsonObject ?: return false
        // Top-level ID-to-object entries define items; mod wrappers can nest definitions.
        return property.parent.parent is JsonFile || definition.findProperty("itemTplToClone") != null
    }

    fun fromJsonLiteral(element: PsiElement): String? {
        if (element !is JsonStringLiteral) return null
        val raw = element.text
        if (raw.length != 26 || raw.first() != '"' || raw.last() != '"') return null
        return normalize(raw.substring(1, 25))
    }

    fun normalize(value: String): String? = value.takeIf {
        it.length == 24 && it.all {
            c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
        }
    }?.lowercase(Locale.ROOT)
}
