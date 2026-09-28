# WTT-RiderIDHighlighter

A JSON and JSONC ID highlighter for JetBrains Rider and IntelliJ IDEA 2025.3 or newer.

- Colors complete 24-character hexadecimal IDs in JSON property names, string values, and arrays.
- Uses text colours `#7B78C8` for root item definitions, `#8D9C6A` for item references, and `#79B5A3` for handbook categories. Each is separately configurable; no background highlight is applied.
- All three ID types use italic text and a single underline, without bold.
- Recognizes custom root IDs without a database entry: top-level ID keys with object values, or nested ID keys whose definition contains `itemTplToClone`. Known handbook categories retain their category colour wherever they appear.
- Supports `.jsonc` files with line comments, block comments, and trailing commas. IDs inside comments are ignored.
- Shows localized item and handbook category names as inline editor hints beside known IDs, with full details on hover, using a bundled SPT 4.1 snapshot: 4,673 item IDs plus 87 category IDs in 17 locales.
- Also includes 558 quest names and 12 trader names. Non-item hints and hover text include their type, such as `(Handbook)`, `(Quest)`, or `(Trader)`; item names remain plain.
- Works immediately after installation, with English names by default. A local SPT database can optionally override the snapshot.
- Automatically discovers WTT CommonLib custom item names in the open project's JSON/JSONC files. Names appear on root definitions and references across files, including unsaved edits, and override bundled/local database names.
- Keeps unknown IDs highlighted without reporting them as errors; instance IDs and other non-item IDs may not have names.
- Loads the database in the background and performs in-memory lookups while highlighting.
- Caches resolved project names and missing IDs in an 8,192-entry per-project LRU cache, separated by locale and same-file overrides. Repeated references reuse their results without querying Rider's index again. Edits, file changes, project-root changes, and indexing transitions invalidate the cache; database definitions are precomputed once per load.
- Opening a project or finishing a build queries the project index in the background and warms the name cache. Refresh requests are coalesced, wait for indexing to finish, and never launch a build or trigger themselves from hint updates.
- Lets you customize ID colors under **Settings > Editor > Color Scheme > WTT IDs**.
- Generates a new MongoDB ObjectId at the editor caret (or replaces a selection) through **Generate MongoID** in the editor right-click menu. The default shortcut is **Ctrl+Alt+Shift+M** on Windows/Linux or **⌘+⌥+⇧+M** on macOS. Each caret gets a distinct ID.
- Inline names are enabled by default. Toggle **WTT names** under **Settings > Editor > Inlay Hints**. Hints use the editor's hint styling and always appear at the end of the line, after all JSON punctuation and comments. Multiple IDs on one line show their names in source order. Hints never become part of the JSON text.

## Install and configure

1. Build with `./gradlew buildPlugin` (Windows: `.\gradlew.bat buildPlugin`).
2. In Rider, open **Settings > Plugins > gear menu > Install Plugin from Disk** and select the ZIP under `build/distributions`.
3. Restart Rider if prompted. Open `examples/wtt-items.json` or `examples/wtt-items.jsonc` to see an item name beside `_tpl`. No database setup is needed. Unknown IDs stay highlighted but have no name hint.
4. Optionally open **Settings > Tools > WTT ID Highlighter** to change the locale or select a local database. Leave the folder blank to use bundled names.

To override bundled names with your current installation, choose:

```text
F:\tarkov_dev\SPT_4.1\SPT_Runtime\SPT_Data
```

A local database must contain `templates/items.json` and `locales/global/<locale>.json`. If present, `templates/handbook.json` supplies the category IDs for `handbookParentId` and other category references. Categories use bare-ID locale keys, while items use `<ID> Name`. Names fall back to English, then the template's internal `_name` for items. Categories with no translated or English label stay highlighted without a name hint. A missing selected locale file is shown as a load error in settings.

Bundled locale codes: `ch`, `cz`, `en`, `es-mx`, `es`, `fr`, `ge`, `hu`, `it`, `jp`, `kr`, `pl`, `po`, `ro`, `ru`, `sk`, `tu` (SPT's own codes). The bundle contains only ID/name/type lookups, not profiles or the full game database. It is a static snapshot; use a local override for newer or exported modded definitions. Local databases can also supply `templates/quests.json` and `traders/*/base.json` with translated names from the global locale files.

Use **Reload database** after changing database files. Loading never edits the SPT database. Items injected only into a running server's memory are not available unless exported into the selected database. The database path and locale are saved in the project workspace settings.

This first version supports JSON and JSONC. Try `examples/wtt-items.jsonc` for a commented example. C# support will require the Rider/ReSharper backend. Matching uses complete, unescaped hexadecimal strings, not ID substrings embedded in prose.

The installed plugin ID remains `ca.bushtail.SPTIDHighlighter` so this renamed plugin updates existing installations and preserves settings. Kotlin sources use `com.wtt.rideridhighlighter` and stay directly in the Kotlin source folders.

## Development

Store all files directly in Git; do not use Git LFS. The root `.gitattributes` disables content filters for every file. Keep that rule and do not add nested LFS overrides.

### WTT CommonLib project names

The plugin recognizes top-level ID-to-item dictionaries containing `itemTplToClone` and `locales.<locale>.name`, matching CommonLib's `CustomItemConfigBase` format. It indexes files in the open project's content roots, including custom resource directories; no server launch, build, or extra path setting is needed. Excluded directories and library files are not searched.

Names use the selected plugin locale, then the first declared non-empty locale, following CommonLib's fallback order. The current file's definition takes precedence. If other project files give the same ID conflicting names, the plugin does not choose an arbitrary file; it falls back to the configured database (or shows no name for an unknown ID).

Editing, adding, or deleting a definition refreshes hints in other open JSON/JSONC files. Cross-file names become available after Rider finishes indexing. Runtime-only C# definitions are not indexed.

WTT quest dictionaries are recognized by their `conditions` and `rewards` objects. Quest titles resolve through the quest's `name` locale key (usually `<ID> name`) in project locale files such as `CustomQuests/<trader>/Locales/en.jsonc`, with `QuestName` as an internal fallback. Trader base definitions are recognized by `_id`, `nickname`, and `loyaltyLevels`; `<ID> Nickname` locale entries override their nickname. Locale entries alone never create quest or trader definitions. Quest and trader references use the reference text style and carry their type in the hint; they are not coloured as root item definitions.

The project index also reads CommonLib's 24-character ID definitions for custom parents, heads, voices, clothing, achievements, weapon presets, hideout recipes, and customizations, including customization storage and hideout customization globals. It reads both JSON and JSONC, dictionary and list formats, and locale entries stored in other files. These definitions use the same bounded per-ID result cache as items, quests, and traders; edits and project/build refreshes invalidate and repopulate it. Services that only register assets or reference existing IDs use names already indexed for those referenced IDs. Symbolic quest zone IDs and arbitrary strings remain outside the 24-character ID highlighter.

### Building

Refresh the committed bundled lookup files using PowerShell 7:

```powershell
.\scripts\Update-BundledDatabase.ps1 -DatabasePath 'F:\tarkov_dev\SPT_4.1\SPT_Runtime\SPT_Data'
```

Normal builds package these resources and do not need an SPT installation.

The default SDK is IntelliJ IDEA 2025.3.6.1, with only Platform and JSON dependencies so the plugin can also load in Rider. Compilation targets Java 21. Use a JDK supported by the Gradle wrapper to run Gradle; Gradle resolves the Java 21 toolchain automatically.

```powershell
.\gradlew.bat test buildPlugin verifyPluginStructure
```

Every push to `main` runs the release workflow. It tests and packages the plugin, then creates a release using `version` in `gradle.properties` as both the tag and release name, with an empty description and the plugin ZIP as its uploaded asset. If that version already has a release, the workflow leaves it unchanged; a new release requires a new version.

To launch a separate development sandbox using an installed Rider:

```powershell
.\gradlew.bat runIde "-PlocalIdePath=C:\Users\enosich\AppData\Local\Programs\Rider"
```

To additionally test loading your real database (read only):

```powershell
$env:SPT_TEST_DATABASE = 'F:\tarkov_dev\SPT_4.1\SPT_Runtime\SPT_Data'
$env:SPT_TEST_WTT_PROJECT = 'F:\tarkov_dev\SPT_4.1\MOD_DEV\Sillyworks'
.\gradlew.bat test --rerun-tasks
```

Tests cover JSON keys/values/arrays, nonmatches, tooltip escaping, cache clearing after configuration changes, localized names, fallbacks, bad paths, malformed data, and cancellation. The installed-database test is skipped unless `SPT_TEST_DATABASE` is set.

To verify compatibility against an installed Rider without changing the compilation SDK:

```powershell
.\gradlew.bat verifyPlugin "-PverificationIdePath=C:\Users\enosich\AppData\Local\Programs\Rider"
```

The project and distribution are named `WTT-RiderIDHighlighter`. The plugin appears in the IDE as **WTT ID Highlighter** to meet JetBrains naming requirements; the Kotlin package remains `com.wtt.rideridhighlighter`.
