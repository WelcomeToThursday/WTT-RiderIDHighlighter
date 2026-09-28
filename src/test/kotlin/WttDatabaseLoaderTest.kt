package com.wtt.rideridhighlighter

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class WttDatabaseLoaderTest {
    @Test fun `bundled database includes all supported locales and valid names`() {
        val locales = listOf("ch", "cz", "en", "es-mx", "es", "fr", "ge", "hu", "it", "jp", "kr", "pl", "po", "ro", "ru", "sk", "tu")
        val english = WttDatabaseLoader.loadBundled("en")
        assertEquals(4760, english.size)
        assertEquals("RGD-5 hand grenade", english["5448be9a4bdc2dfd2f8b456a"])
        assertEquals("Assault carbines", english["5b5f78e986f77447ed5636b1"])
        assertEquals("Assault rifles", english["5b5f78fc86f77409407a7f90"])
        val index = WttDatabaseLoader.loadBundledIndex("en")
        assertEquals(english, index.names)
        assertEquals(87, index.handbookIds.size)
        assertTrue("5b5f78e986f77447ed5636b1" in index.handbookIds)
        assertFalse("5448be9a4bdc2dfd2f8b456a" in index.handbookIds)
        assertEquals("Debut", index.quests["5936d90786f7742b1420ba5b"])
        assertEquals("Prapor", index.traders["54cb50c76803fa8b248b4571"])
        for (locale in locales) {
            val localized = WttDatabaseLoader.loadBundledIndex(locale)
            val names = localized.names
            assertEquals(english.keys, names.keys)
            assertTrue(names.values.all { it.isNotBlank() })
            assertEquals(index.quests.keys, localized.quests.keys)
            assertEquals(index.traders.keys, localized.traders.keys)
            assertTrue(localized.quests.values.all { it.isNotBlank() })
            assertTrue(localized.traders.values.all { it.isNotBlank() })
        }
        assertThrows(IllegalStateException::class.java) { WttDatabaseLoader.loadBundled("missing-locale") }
        assertThrows(IllegalArgumentException::class.java) { WttDatabaseLoader.loadBundled("../en") }
    }

    @get:Rule val temporary = TemporaryFolder()
    private val first = "5448be9a4bdc2dfd2f8b456a"
    private val second = "5448be9a4bdc2dfd2f8b456b"

    private fun write(root: Path, file: String, content: String) {
        Files.createDirectories(root.resolve(file).parent)
        Files.writeString(root.resolve(file), content)
    }

    @Test fun `loads handbook category labels with translated and English fallbacks`() {
        val root = temporary.newFolder().toPath()
        val category = "5b5f78e986f77447ed5636b1"
        val fallback = "5b5f78fc86f77409407a7f90"
        val untranslated = "6564b96a189fe36f356d177c"
        write(root, "templates/items.json", """{"$first":{"_name":"internal"}}""")
        write(root, "templates/handbook.json", """{"Categories":[{"Id":"$category","ParentId":null},{"Id":"$fallback"},{"Id":"$untranslated"}],"Items":[{"Id":"$first","ParentId":"$category","Price":1}]}""")
        write(root, "locales/global/en.json", """{"$first Name":"Grenade","$category":"Assault carbines","$fallback":"Assault rifles","$category Name":"Wrong suffix"}""")
        write(root, "locales/global/fr.json", """{"$category":"Carabines d'assaut","$fallback":""}""")
        val names = WttDatabaseLoader.load(root.toString(), "fr")
        assertEquals("Carabines d'assaut", names[category])
        assertEquals("Assault rifles", names[fallback])
        assertEquals(untranslated, names[untranslated])
        assertEquals("Grenade", names[first])
    }

    @Test fun `bare locale keys only resolve handbook categories and cannot override items`() {
        val root = temporary.newFolder().toPath()
        write(root, "templates/items.json", """{"$first":{"_name":"internal"}}""")
        write(root, "templates/handbook.json", """{"Categories":[{"Id":"$first"},{"Id":"invalid"}],"Items":[{"Id":"$second"}]}""")
        write(root, "locales/global/en.json", """{"$first Name":"Item name","$first":"Wrong category name","$second":"Unrelated quest text"}""")
        val index = WttDatabaseLoader.loadIndex(root.toString(), "en")
        assertEquals(mapOf(first to "Item name"), index.names)
        assertTrue(index.handbookIds.isEmpty())
    }

    @Test fun `malformed handbook fails instead of silently dropping categories`() {
        val root = temporary.newFolder().toPath()
        write(root, "templates/items.json", "{}")
        write(root, "templates/handbook.json", "broken")
        write(root, "locales/global/en.json", "{}")
        assertThrows(Exception::class.java) { WttDatabaseLoader.load(root.toString(), "en") }
    }

    @Test fun `local quests and traders use locale names and internal fallbacks`() {
        val root = temporary.newFolder().toPath()
        val quest = "5936d90786f7742b1420ba5b"
        val trader = "54cb50c76803fa8b248b4571"
        write(root, "templates/items.json", "{}")
        write(root, "templates/quests.json", """{"$quest":{"QuestName":"Internal quest","name":"custom quest title"}}""")
        write(root, "traders/$trader/base.json", """{"_id":"$trader","nickname":"Internal trader"}""")
        write(root, "locales/global/en.json", """{"custom quest title":"English quest","$trader Nickname":"Prapor"}""")
        write(root, "locales/global/fr.json", """{"custom quest title":"Quête locale"}""")
        val index = WttDatabaseLoader.loadIndex(root.toString(), "fr")
        assertEquals("Quête locale", index.quests[quest])
        assertEquals("Prapor", index.traders[trader])
        assertTrue(index.names.isEmpty())
    }

    @Test fun `loads names from SPT Data directory with locale and internal fallbacks`() {
        val root = temporary.newFolder().toPath()
        write(root, "database/templates/items.json", """{"$first":{"_name":"internal","_props":{"ignored":[1,2]}},"$second":{"_name":"fallback"}}""")
        write(root, "database/locales/global/en.json", """{"$first Name":"English name","$first Description":"Not a name"}""")
        write(root, "database/locales/global/fr.json", """{"$first Name":"Nom français","$second Name":""}""")
        val names = WttDatabaseLoader.load(root.toString(), "fr")
        assertEquals(mapOf(first to "Nom français", second to "fallback"), names)
    }

    @Test fun `uses English when selected translation omits item`() {
        val root = temporary.newFolder().toPath()
        write(root, "templates/items.json", """{"$first":{"_name":"internal"}}""")
        write(root, "locales/global/en.json", """{"$first Name":"English name"}""")
        write(root, "locales/global/fr.json", "{}")
        assertEquals("English name", WttDatabaseLoader.load(root.toString(), "fr")[first])
    }

    @Test fun `reports missing database and locale`() {
        val root = temporary.newFolder().toPath()
        assertThrows(IllegalStateException::class.java) { WttDatabaseLoader.load(root.toString(), "en") }
        write(root, "templates/items.json", "{}")
        assertThrows(IllegalArgumentException::class.java) { WttDatabaseLoader.load(root.toString(), "en") }
        assertThrows(IllegalArgumentException::class.java) { WttDatabaseLoader.load(root.toString(), "../en") }
    }

    @Test fun `rejects malformed data and honors cancellation`() {
        val root = temporary.newFolder().toPath()
        write(root, "templates/items.json", "broken")
        assertThrows(Exception::class.java) { WttDatabaseLoader.load(root.toString(), "en") }
        write(root, "templates/items.json", """{"$first":{}}""")
        assertThrows(InterruptedException::class.java) {
            WttDatabaseLoader.load(root.toString(), "en") { throw InterruptedException() }
        }
    }

    @Test fun `can read installed SPT database when explicitly supplied`() {
        val path = System.getenv("SPT_TEST_DATABASE")
        assumeTrue("Set SPT_TEST_DATABASE to run the real database check", !path.isNullOrBlank())
        val names = WttDatabaseLoader.load(path!!, "en")
        assertTrue("Expected a populated SPT database", names.size > 1000)
        assertFalse("Expected localized food item name", names[first].isNullOrBlank())
        assertNotEquals(first, names[first])
        assertEquals("Assault carbines", names["5b5f78e986f77447ed5636b1"])
    }
}
