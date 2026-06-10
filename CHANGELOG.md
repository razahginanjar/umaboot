# Changelog

## v0.1.1 - 2026-06-10

### Added

- Added overlay dependency planning for existing Maven, Gradle Kotlin, and Gradle Groovy projects.
- Added Preview / Merge patch generation for missing or wrong-scope build dependencies required by generated code.
- Added Preview / Merge patch generation for required application configuration instead of writing overlay config directly.
- Added CLI `generate --overlay-target` support so plugin previews can render into a temporary directory while comparing against the real project.
- Added overlay dependency and application-config checks to CLI `generate` and `diff` output.
- Added IntelliJ Sync from YAML and Revert actions for the tool window form.
- Added IntelliJ Reset Preview / Merge action and session state so active merge choices can be invalidated safely.
- Added VS Code Sync from YAML support in the config editor.
- Added VS Code Reset Preview / Merge command and dashboard entry.

### Changed

- Changed overlay generation so `generate` writes new files but leaves modified files, build-file patches, and application-config patches for Preview / Merge.
- Changed overlay notifications to report total changes that need Preview / Merge, not only modified generated files.
- Changed IntelliJ tool window actions into separate header and workflow toolbars:
  - Header: Revert, Sync, Logs.
  - Footer: Apply, Generate, Preview / Merge.
- Changed IntelliJ form behavior so Use MapStruct is shown only for JPA persistence.
- Changed IntelliJ form behavior so MyBatis style is shown only for MyBatis persistence.
- Changed VS Code config editor behavior so Use MapStruct is shown only for JPA persistence.
- Changed IntelliJ form inputs to use a 280px preferred width while still stretching with the tool window.
- Moved IntelliJ help buttons beside the attribute label, including the custom Strip prefix from class names row.
- Changed VS Code Preview / Merge to render overlay previews against the real output target using a temporary preview directory.
- Changed VS Code config-editor sync failures so malformed YAML does not replace the current form state.

### Fixed

- Fixed overlay Preview / Merge coverage for generated build dependency patches.
- Fixed overlay Preview / Merge coverage for application config additions.
- Fixed IntelliJ Preview / Merge so reset sessions skip stale merge results instead of writing after reset.
- Fixed IntelliJ persistence option visibility so non-JPA persistence cannot save `useMapStruct: true`.
- Fixed VS Code config saving so non-JPA persistence cannot save `useMapStruct: true`.

### Tests

- Added tests for build-file dependency planning.
- Added tests for overlay planning with preview-only patches.
- Added tests for application-config preview planning.
