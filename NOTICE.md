# SPT data attribution

The bundled ID and name lookup files in `src/main/resources/spt/` are derived from the SPT 4.1 database distributed with **Single Player Tushonka**. That project credits **Archangel** and is based on the [SPT C# server](https://github.com/sp-tarkov/server-csharp). See the [SP-Tushonka source and license](https://github.com/SP-Tushonka/server-csharp).

The source distribution identifies its license as **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0)**. A copy of its license notice and full legal text is included at [src/main/resources/spt/CC-BY-NC-SA-4.0-LICENSE.txt](src/main/resources/spt/CC-BY-NC-SA-4.0-LICENSE.txt); the [official license](https://creativecommons.org/licenses/by-nc-sa/4.0/) is also available from Creative Commons.

This plugin's snapshot was generated on 2026-09-28 from `SPT_Runtime/SPT_Data/database` using `scripts/Update-BundledDatabase.ps1`. It extracts and sorts 24-character IDs and localized item, handbook-category, quest, and trader names into smaller lookup files. Missing translations fall back to English or an internal name. The original database structure and full templates are not included.

This notice concerns the bundled SPT-derived data. It does not purport to license this plugin's Kotlin source code or independently authored project files under CC BY-NC-SA 4.0. No endorsement by SPT or SP-Tushonka is implied.
