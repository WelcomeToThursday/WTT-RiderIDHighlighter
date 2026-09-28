package com.wtt.rideridhighlighter

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID as IndexId
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import java.io.DataInput
import java.io.DataOutput

/** Locale names are indexed independently of the user's selected locale. */
class WttItemNameIndex : FileBasedIndexExtension<String, Map<String, String>>() {
    override fun getName() = WttItemNameIndexObject.NAME
    override fun getVersion() = 4
    override fun dependsOnFileContent() = true
    override fun getKeyDescriptor(): EnumeratorStringDescriptor = EnumeratorStringDescriptor.INSTANCE
    override fun getInputFilter() = FileBasedIndex.InputFilter {
        it.extension?.lowercase() in setOf("json", "jsonc")
    }
    override fun getIndexer() = DataIndexer<String, Map<String, String>, FileContent> { content ->
        WttItemNameIndexObject.readDefinitions(content.psiFile as? JsonFile)
    }
    override fun getValueExternalizer() = object : DataExternalizer<Map<String, String>> {
        override fun save(out: DataOutput, value: Map<String, String>) {
            out.writeInt(value.size)
            for ((locale, name) in value) {
                com.intellij.util.io.IOUtil.writeUTF(out, locale)
                com.intellij.util.io.IOUtil.writeUTF(out, name)
            }
        }
        override fun read(input: DataInput): Map<String, String> = buildMap {
            repeat(input.readInt()) {
                put(com.intellij.util.io.IOUtil.readUTF(input), com.intellij.util.io.IOUtil.readUTF(input))
            }
        }
    }

    object WttItemNameIndexObject {
        val NAME: IndexId<String, Map<String, String>> = IndexId.create("com.wtt.rideridhighlighter.wttItemNames")

        internal fun readDefinitions(file: JsonFile?): Map<String, Map<String, String>> {
            if (file == null) return emptyMap()
            val result = linkedMapOf<String, Map<String, String>>()
            val path = file.virtualFile.path.replace('\\', '/').lowercase()
            val root = file.topLevelValue
            if (root is JsonObject) {
                val traderId = root.id("_id")
                val nickname = root.string("nickname")
                if (traderId != null && !nickname.isNullOrBlank() && root.findProperty("loyaltyLevels")?.value is JsonArray) {
                    result[traderId] = typed(WttDefinitionKind.TRADER, nickname, "$traderId Nickname")
                }
                addStandalone(root, path, result)
                for (property in root.propertyList) {
                ProgressManager.checkCanceled()
                val value = property.value
                if (value is JsonStringLiteral && value.value.isNotBlank()) {
                    result["@locale:${property.name}"] = mapOf(file.name.substringBeforeLast('.') to value.value)
                    continue
                }
                val id = WttId.fromJsonLiteral(property.nameElement) ?: continue
                val item = property.value as? JsonObject ?: continue
                if (item.findProperty("conditions")?.value is JsonObject && item.findProperty("rewards")?.value is JsonObject) {
                    result[id] = typed(WttDefinitionKind.QUEST, item.string("QuestName") ?: id, item.string("name") ?: "$id name")
                    continue
                }
                val clone = item.string("itemTplToClone")
                if (!clone.isNullOrBlank()) {
                    val names = item.localeNames()
                    if (names.isNotEmpty()) result[id] = names
                    continue
                }
                val typed = dictionaryDefinition(id, item, path)
                if (typed != null) result[id] = typed
                }
            } else if (root is JsonArray) {
                for (value in root.valueList) {
                    ProgressManager.checkCanceled()
                    val entry = value as? JsonObject ?: continue
                    addStandalone(entry, path, result)
                    // CustomParents also accepts an array of ID-to-parent dictionaries.
                    if (path.contains("/customparents/")) for (property in entry.propertyList) {
                        val id = WttId.fromJsonLiteral(property.nameElement) ?: continue
                        val parent = property.value as? JsonObject ?: continue
                        dictionaryDefinition(id, parent, path)?.let { result[id] = it }
                    }
                }
            }
            return result
        }

        private fun JsonObject.string(key: String): String? =
            (findProperty(key)?.value as? JsonStringLiteral)?.value?.takeIf(String::isNotBlank)

        private fun JsonObject.id(key: String): String? =
            findProperty(key)?.value?.let(WttId::fromJsonLiteral)

        private fun JsonObject.localeNames(): Map<String, String> {
            val locales = findProperty("locales")?.value as? JsonObject ?: return emptyMap()
            return buildMap {
                for (locale in locales.propertyList) {
                    val name = when (val value = locale.value) {
                        is JsonStringLiteral -> value.value
                        is JsonObject -> value.string("name")
                        else -> null
                    }
                    if (!name.isNullOrBlank()) put(locale.name, name)
                }
            }
        }

        private fun typed(kind: WttDefinitionKind, name: String, localeKey: String? = null, names: Map<String, String> = emptyMap()): Map<String, String> =
            buildMap {
                put("!kind", kind.label!!)
                put("!default", name)
                if (localeKey != null) put("!localeKey", localeKey)
                putAll(names)
            }

        private fun dictionaryDefinition(id: String, entry: JsonObject, path: String): Map<String, String>? {
            val locales = entry.localeNames()
            return when {
                path.contains("/customparents/") && entry.id("_id") == id && entry.string("_type") == "Node" ->
                    entry.string("_name")?.let { typed(WttDefinitionKind.ITEM_PARENT, it) }
                path.contains("/customheads/") && entry.findProperty("path") != null && locales.isNotEmpty() ->
                    typed(WttDefinitionKind.HEAD, locales.values.first(), names = locales)
                path.contains("/customvoices/") && entry.findProperty("bundlePath") != null ->
                    (entry.string("name") ?: locales.values.firstOrNull())?.let { typed(WttDefinitionKind.VOICE, it, names = locales) }
                path.contains("/customweaponpresets/") && entry.string("_type") == "Preset" && entry.id("_id") == id ->
                    entry.string("_name")?.let { typed(WttDefinitionKind.PRESET, it) }
                path.contains("/customcustomization/") && entry.id("_id") == id && entry.findProperty("_props") != null ->
                    entry.string("_name")?.let { typed(WttDefinitionKind.CUSTOMIZATION, it) }
                else -> null
            }
        }

        private fun addStandalone(entry: JsonObject, path: String, result: MutableMap<String, Map<String, String>>) {
            when {
                path.contains("/customclothing/") && entry.string("type") != null -> {
                    val locales = entry.localeNames()
                    val name = locales.values.firstOrNull() ?: return
                    val definition = typed(WttDefinitionKind.CLOTHING, name, names = locales)
                    for (field in listOf("suiteId", "outfitId", "topId", "handsId", "bottomId"))
                        entry.id(field)?.let { result[it] = definition }
                }
                path.contains("/customachievements/achievements/") -> {
                    val id = entry.id("id") ?: return
                    result[id] = typed(WttDefinitionKind.ACHIEVEMENT, id, entry.string("name") ?: "$id name")
                }
                path.contains("/customhideoutrecipes/") && entry.findProperty("requirements")?.value is JsonArray -> {
                    val id = entry.id("_id") ?: return
                    val product = entry.string("endProduct")
                    val name = entry.string("name") ?: product?.let { "Craft for $it" } ?: "Hideout craft"
                    result[id] = typed(WttDefinitionKind.RECIPE, name)
                }
                path.contains("/customcustomization/hideoutcustomizationglobals/") -> {
                    val id = entry.id("id") ?: return
                    entry.string("systemName")?.let { result[id] = typed(WttDefinitionKind.HIDEOUT_CUSTOMIZATION, it) }
                }
                path.contains("/customcustomization/customizationstorage/") -> {
                    val id = entry.id("id") ?: return
                    val name = entry.string("name") ?: entry.string("type") ?: return
                    result[id] = typed(WttDefinitionKind.CUSTOMIZATION_STORAGE, name)
                }
                path.contains("/customdialogues/") -> {
                    val id = entry.id("Id") ?: entry.id("id") ?: return
                    val name = entry.string("name") ?: entry.string("message") ?: entry.string("text") ?: return
                    result[id] = typed(WttDefinitionKind.DIALOGUE, name)
                }
            }
        }
    }

}
