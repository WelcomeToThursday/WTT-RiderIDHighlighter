# WTT-RiderIDHighlighter Changelog

## [Unreleased]

## [1.0.0] - 2026-09-28

### Added
- Highlight complete item, root item, and handbook IDs in JSON and JSONC with separate configurable colours, italics, and underlines.
- Show localized item names and typed handbook, quest, and trader names as end-of-line hints and on hover.
- Bundle SPT 4.1 names for 4,673 items, 87 handbook categories, 558 quests, and 12 traders across 17 locales, with an optional local database override.
- Discover WTT CommonLib item, quest, and trader names from project JSON and JSONC files, including unsaved edits and cross-file references.
- Cache name lookups and refresh them when projects open, files change, or builds finish.
- Provide Rider settings for the database, locale, colours, and hints.
