# Third-party components

RobustPlainGameMaker Player - MyVersion ("RPGM Player - MV") is licensed under GPL-3.0-or-later. Every third-party library, plugin or shader
used by the app is listed here with its license, which must be compatible with
GPL-3.0-or-later (for example MIT, BSD, Apache-2.0, public domain, GPL-2.0-or-later or
GPL-3.0; not GPL-2.0-only and not files without a license grant). Ported code keeps its original
copyright and license header and is marked as modified.

## Libraries (Gradle dependencies, not modified)

| Component | Version | License | Use |
|---|---|---|---|
| AndroidX Core KTX | 1.19.1 | Apache-2.0 | Android helpers |
| AndroidX Activity (KTX, Compose) | 1.13.0 | Apache-2.0 | activities, result APIs |
| AndroidX Lifecycle ViewModel Compose | 2.11.0 | Apache-2.0 | view model |
| Jetpack Compose (UI, Material 3, Material Icons Core) | BOM 2026.09.00 | Apache-2.0 | user interface |

## Fonts (bundled, not modified)

Shipped unmodified in `app/src/main/assets/fonts/<name>/` with their licence file
(`OFL.txt`), taken from the Google Fonts repository (github.com/google/fonts, commit
45e3d5269e4a17d823f6d84470bc084dc5ae46f6). The SIL Open Font License 1.1 allows
bundling fonts with software under any licence; the fonts themselves stay under the OFL
and may not be sold on their own.

| Font | Copyright | License |
|---|---|---|
| Pixelify Sans | 2021 The Pixelify Sans Project Authors | OFL-1.1 |
| VT323 | 2011 The VT323 Project Authors | OFL-1.1 |
| Press Start 2P | 2012 The Press Start 2P Project Authors (Reserved Font Name "Press Start 2P") | OFL-1.1 |
| Atkinson Hyperlegible | 2020 Braille Institute of America, Inc. | OFL-1.1 |

## Test-only dependencies (not shipped in the app)

Used only to run unit tests on the development machine; they are not part of the
distributed app, so the GPL compatibility rule for shipped components does not apply.

| Component | Version | License |
|---|---|---|
| JUnit | 4.13.2 | EPL-1.0 |
| org.json (JSON-java) | 20260814 | Public domain |

## Ported source code

None. The picture filters (`mv/picture.js`) are original code. Code ported later is added
here with repository, commit hash, file and the modifications made.
