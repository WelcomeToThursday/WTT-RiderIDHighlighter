package com.wtt.rideridhighlighter

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.nio.file.Files
import java.nio.file.Path

internal object WttDatabaseLoader {
    data class Index(val names: Map<String, String> = emptyMap(), val handbookIds: Set<String> = emptySet(), val quests: Map<String, String> = emptyMap(), val traders: Map<String, String> = emptyMap()) {
        // Resolve database types once per load, rather than allocating a definition per occurrence.
        val definitions: Map<String, WttDefinition> = buildMap {
            traders.forEach { (id, name) -> put(id, WttDefinition(name, WttDefinitionKind.TRADER)) }
            quests.forEach { (id, name) -> put(id, WttDefinition(name, WttDefinitionKind.QUEST)) }
            names.forEach { (id, name) -> put(id, WttDefinition(name, if (id in handbookIds) WttDefinitionKind.HANDBOOK else WttDefinitionKind.ITEM)) }
        }
    }

    fun loadBundledIndex(locale: String, checkCanceled: () -> Unit = {}): Index {
        val names = loadBundled(locale, checkCanceled)
        val stream = javaClass.getResourceAsStream("/spt/handbook-category-ids.json")
            ?: error("Bundled handbook category index is missing.")
        val categories = JsonReader(stream.bufferedReader(Charsets.UTF_8)).use { reader ->
            val ids = linkedSetOf<String>()
            reader.beginArray()
            while (reader.hasNext()) {
                checkCanceled()
                val id = reader.nextString()
                require(WttId.normalize(id) == id && id in names) {
                    "Invalid bundled category ID: $id"
                }
                ids.add(id)
            }
            reader.endArray()
            ids.toSet()
        }
        return Index(names, categories, WttNonItemNames.bundled("quest-names", locale, checkCanceled), WttNonItemNames.bundled("trader-names", locale, checkCanceled))
    }

    private val databasePaths = listOf("", "database", "SPT_Data/database", "SPT_Runtime/SPT_Data/database", "SPT_Data/Server/database", "Aki_Data/Server/database")

    fun loadBundled(locale: String, checkCanceled: () -> Unit = {}): Map<String, String> {
        require(locale.matches(Regex("[a-zA-Z0-9_-]+"))) {
            "Enter a locale code such as en or fr."
        }
        val stream = javaClass.getResourceAsStream("/spt/item-names/$locale.json")
            ?: error("No bundled names for locale '$locale'. Use en or choose a local database with this locale.")
        return JsonReader(stream.bufferedReader(Charsets.UTF_8)).use { reader ->
            val names = linkedMapOf<String, String>()
            reader.beginObject()
            while (reader.hasNext()) {
                checkCanceled()
                val id = reader.nextName()
                val name = reader.nextString()
                require(WttId.normalize(id) == id && name.isNotBlank()) { "Invalid bundled item name: $id" }
                names[id] = name
            }
            reader.endObject()
            names.toMap()
        }
    }

    fun findDatabase(path: String): Path {
        val root = Path.of(path)
        return databasePaths.map(root::resolve).firstOrNull {
            Files.isRegularFile(it.resolve("templates/items.json"))
        } ?: error("No templates/items.json found. Select your SPT installation or database folder.")
    }

    private fun handbookCategoryIds(database: Path, checkCanceled: () -> Unit): Set<String> {
        val file = database.resolve("templates/handbook.json")
        if (!Files.isRegularFile(file)) {
            return emptySet()
        }
        val ids = linkedSetOf<String>()
        JsonReader(Files.newBufferedReader(file)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                checkCanceled()
                if (reader.nextName() != "Categories") {
                    reader.skipValue()
                    continue
                }
                reader.beginArray()
                while (reader.hasNext()) {
                    checkCanceled()
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "Id" && reader.peek() == JsonToken.STRING) {
                            WttId.normalize(reader.nextString())?.let(ids::add)
                        } else reader.skipValue()
                    }
                    reader.endObject()
                }
                reader.endArray()
            }
            reader.endObject()
        }
        return ids
    }

    fun load(path: String, locale: String, checkCanceled: () -> Unit = {}): Map<String, String> =
        loadIndex(path, locale, checkCanceled).names

    fun loadIndex(path: String, locale: String, checkCanceled: () -> Unit = {}): Index {
        require(locale.matches(Regex("[a-zA-Z0-9_-]+"))) { "Enter a locale code such as en or fr." }
        val database = findDatabase(path)
        val names = linkedMapOf<String, String>()
        JsonReader(Files.newBufferedReader(database.resolve("templates/items.json"))).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                checkCanceled()
                val id = WttId.normalize(reader.nextName())
                if (id == null || reader.peek() != JsonToken.BEGIN_OBJECT) {
                    reader.skipValue()
                    continue
                }
                var name: String? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "_name" && reader.peek() == JsonToken.STRING) {
                        name = reader.nextString()
                    }
                    else reader.skipValue()
                }
                reader.endObject()
                names[id] = name?.takeIf(String::isNotBlank) ?: id
            }
            reader.endObject()
        }
        val itemIds = names.keys.toSet()
        val categoryIds = handbookCategoryIds(database, checkCanceled) - itemIds
        categoryIds.forEach { names[it] = it }
        // Category locale keys are bare IDs; item names use the " Name" suffix.
        // Restrict bare keys to handbook categories so quest text is not indexed.
        // English is a fallback when a translated name is absent.
        for (code in listOf("en", locale).distinct()) {
            val file = database.resolve("locales/global/$code.json")
            if (!Files.isRegularFile(file)) {
                require(code != locale) { "Locale file not found: $file" }
                continue
            }
            JsonReader(Files.newBufferedReader(file)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    checkCanceled()
                    val key = reader.nextName()
                    val id = if (key.endsWith(" Name")) {
                        WttId.normalize(key.removeSuffix(" Name"))?.takeIf {
                            it in itemIds
                        }
                    } else WttId.normalize(key)?.takeIf {
                        it in categoryIds
                    }
                    if (id != null && reader.peek() == JsonToken.STRING) {
                        reader.nextString().takeIf(String::isNotBlank)?.let {
                            names[id] = it
                        }
                    } else reader.skipValue()
                }
                reader.endObject()
            }
        }
        return Index(names.toMap(), categoryIds, WttNonItemNames.quests(database, locale, checkCanceled), WttNonItemNames.traders(database, locale, checkCanceled))
    }
}
