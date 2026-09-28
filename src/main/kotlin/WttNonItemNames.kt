package com.wtt.rideridhighlighter

import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.nio.file.Files
import java.nio.file.Path

internal object WttNonItemNames {
    fun bundled(folder: String, locale: String, checkCanceled: () -> Unit): Map<String, String> {
        val stream = javaClass.getResourceAsStream("/spt/$folder/$locale.json") ?: return emptyMap()
        return JsonReader(stream.bufferedReader(Charsets.UTF_8)).use { readNames(it, checkCanceled) }
    }

    private fun readNames(reader: JsonReader, checkCanceled: () -> Unit): Map<String, String> = buildMap {
        reader.beginObject()
        while (reader.hasNext()) {
            checkCanceled()
            val id = reader.nextName()
            val name = reader.nextString()
            require(WttId.normalize(id) == id && name.isNotBlank()) {
                "Invalid definition name: $id"
            }
            put(id, name)
        }
        reader.endObject()
    }

    fun quests(database: Path, locale: String, checkCanceled: () -> Unit): Map<String, String> {
        val quests = database.resolve("templates/quests.json")
        if (!Files.isRegularFile(quests)) return emptyMap()
        val names = linkedMapOf<String, String>()
        val localeKeys = linkedMapOf<String, String>()
        JsonReader(Files.newBufferedReader(quests)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                checkCanceled()
                val id = WttId.normalize(reader.nextName())
                if (id == null || reader.peek() != JsonToken.BEGIN_OBJECT) {
                    reader.skipValue()
                    continue
                }
                val quest = JsonParser.parseReader(reader).asJsonObject
                fun text(key: String) = quest[key]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                names[id] = text("QuestName")?.takeIf(String::isNotBlank) ?: id
                localeKeys[text("name") ?: "$id name"] = id
            }
            reader.endObject()
        }
        return localize(database, locale, names, localeKeys, checkCanceled)
    }

    fun traders(database: Path, locale: String, checkCanceled: () -> Unit): Map<String, String> {
        val folder = database.resolve("traders")
        if (!Files.isDirectory(folder)) return emptyMap()
        val names = linkedMapOf<String, String>()
        val localeKeys = linkedMapOf<String, String>()
        Files.list(folder).use { directories ->
            directories.sorted().forEach { directory ->
                checkCanceled()
                val file = directory.resolve("base.json")
                if (Files.isRegularFile(file)) {
                    val trader = Files.newBufferedReader(file).use { JsonParser.parseReader(it).asJsonObject }
                    val id = trader["_id"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let(WttId::normalize)
                    if (id != null) {
                        val name = trader["nickname"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                        names[id] = name?.takeIf(String::isNotBlank) ?: id
                        localeKeys["$id Nickname"] = id
                    }
                }
            }
        }
        return localize(database, locale, names, localeKeys, checkCanceled)
    }

    private fun localize(database: Path, locale: String, names: MutableMap<String, String>, localeKeys: Map<String, String>, checkCanceled: () -> Unit): Map<String, String> {
        for (code in listOf("en", locale).distinct()) {
            val file = database.resolve("locales/global/$code.json")
            if (!Files.isRegularFile(file)) continue
            JsonReader(Files.newBufferedReader(file)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    checkCanceled()
                    val id = localeKeys[reader.nextName()]
                    if (id != null && reader.peek() == JsonToken.STRING) {
                        reader.nextString().takeIf(String::isNotBlank)?.let { names[id] = it }
                    } else reader.skipValue()
                }
                reader.endObject()
            }
        }
        return names
    }
}
