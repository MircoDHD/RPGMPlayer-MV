# Third-party components

RobustPlainGameMaker Player - MyVersion ("RPGM Player") is licensed under GPL-3.0-or-later. Every third-party library, plugin or shader
used by the app is listed here with its license, which must be compatible with
GPL-3.0-or-later (see `CLAUDE.md` → Conventions). Ported code keeps its original
copyright and license header and is marked as modified.

## Libraries (Gradle dependencies, not modified)

| Component | Version | License | Use |
|---|---|---|---|
| AndroidX Core KTX | 1.19.1 | Apache-2.0 | Android helpers |
| AndroidX Activity (KTX, Compose) | 1.13.0 | Apache-2.0 | activities, result APIs |
| AndroidX Lifecycle ViewModel Compose | 2.11.0 | Apache-2.0 | view model |
| Jetpack Compose (UI, Material 3, Material Icons Core) | BOM 2026.09.00 | Apache-2.0 | user interface |

## Test-only dependencies (not shipped in the app)

Used only to run unit tests on the development machine; they are not part of the
distributed app, so the GPL compatibility rule for shipped components does not apply.

| Component | Version | License |
|---|---|---|
| JUnit | 4.13.2 | EPL-1.0 |
| org.json (JSON-java) | 20260814 | Public domain |

## Ported source code

None yet. Planned shader ports for the post-processing module are listed with their
licenses in `docs/feature-plan.md` (B3); each one is added here when it is imported,
with repository, commit hash, file and the modifications made.
