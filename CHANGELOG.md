# Changelog

All notable changes to RobustPlainGameMaker Player - MyVersion ("RPGM Player - MV").
Versions follow `major.minor.patch`: the minor digit for new features, the patch digit for
fixes. Dates are build dates.

## 0.6.0 – 2026-10-21 (Phase D: saves)
### Added
- **Auto saves**: every 15 minutes of play the app saves the game at a calm moment, so
  stopping suddenly loses little. The 3 newest are kept, up to 3 more can be kept for good.
  Separate from the game's own saves; load them from the quick menu or from the list in
  the library, export and import them as a zip. "Play" always starts the game normally.
- **Game saves** in the library: the slots of the game's own save menu at a glance.
- **Game patch guide**: step by step, in the settings; per game it creates the patch
  folder and adds a picked script.
### Fixed
- Saves are written through to the storage before they replace the old file, so a crash
  or power loss right after saving can no longer leave an empty save.

## 0.5.0 – 2026-10-21 (Phase C: compatibility)
### Added
- **Fonts** for the game's text, globally and per game: Pixelify Sans, VT323, Press Start 2P,
  Atkinson Hyperlegible or your own font file, with an adjustable text size.
- **App font**, chosen separately from the font in games.
- **App language** (system, German, English); on Android 13+ also in the system settings.
- When a game stops on an error, a dialog offers to continue anyway or quit.
- Option "Continue after errors" (globally and per game): errors and missing files are
  skipped instead of stopping the game.
- **Game patches**: scripts in a game's `mvplayer-patches` folder run before or after the
  plugins (see docs/patches.md); built-in patches are chosen by data-driven profiles.
- Plugins made for the PC version that call NW.js or Node.js functions no longer break.
### Fixed
- A missing game file stopped loading forever; it now shows MV's loading error, as on PC.
- Files with garbled or differently encoded names (wrong zip code page, macOS archives)
  are found.
- Data files with comments, trailing commas or a byte order mark load.
- Plain images and audio added by mods to encrypted games load.
- Game messages (`alert`) appear as the app's own dialog.

## 0.4.4 – 2026-10-21 (Phase B, part 3: picture)
### Added
- **Picture and filters**, globally and per game: scaling (fit, integer, stretch),
  enlargement (smooth, sharp, pixel), scanlines, sharpening, brightness, contrast and
  saturation.
- In game, the quick menu opens them beside the game, which shows every change at once.

## 0.4.3 – 2026-10-21
### Fixed
- **Thin lines between map tiles** on some GPUs (Mali, as in many MediaTek and Exynos
  devices): shaders now use high precision where the GPU supports it.
- **Haptic feedback** did nothing when Android's own touch vibration was switched off. It
  now drives the vibration motor directly, both for the on-screen controls and for the
  short pulse when you switch it on.
- **The save and load menus open much faster** on slow storage: save files that were just
  read or written are kept in memory instead of being read again for every slot. Saving
  is faster too.
- Text drawn with an invalid alignment no longer floods the log with browser warnings.
### Changed
- **The frame graph is its own HUD module**, so the frame time can be shown without it.
  Existing HUD setups keep the graph.
- Starting a game right after the library scanned it skips the second scan.
- Diagnostic logs record frame rate and device state (battery, heat, memory) once a
  minute, to find slowdowns in long sessions.

## 0.4.2 – 2026-10-19
### Added
- HUD colours: frame rate, frame time (graph included) and 1 % low turn green, yellow or
  red against the chosen frame rate (at 30 fps, 30 is green and yellow starts below 24);
  other values only turn yellow or red near a limit (low battery, hot battery, heat,
  JavaScript memory or time, slow game logic).

## 0.4.1 – 2026-10-19 (Phase B, part 2: frame rates)
### Added
- **Frame rate options** globally and per game: 30 fps (battery saver), 60 fps (default) and
  the display rate (*experimental*). They apply immediately, also during a game.
- ⓘ buttons in the frame rate and renderer choices explain each option in plain words:
  when it is useful, pros and cons.
### Fixed
- The in-game play time is now correct at every frame rate. MV counted drawn frames, so
  play time ran twice as fast at 120 Hz and would have run at half speed at 30 fps; each
  frame now adds the game logic steps it shows.
### Changed
- The global "Limit to 60 fps" switch became a frame rate choice.
- Choice descriptions for Canvas and WebGL 2 now say when they are useful.

## 0.4.0 – 2026-10-19 (Phase B, part 1: renderer and performance HUD)
### Added
- **Renderer choice**, globally and per game: WebGL (default), Canvas, or WebGL 2
  (*experimental*; falls back to WebGL when it cannot start). Changing it during a game
  offers to restart the game, because the renderer is chosen when the game starts.
- **Performance HUD** above the game, switched on in Settings, in the quick menu or with a
  controller button you assign. Modules: FPS, frame time with graph, 1 % low and slow
  frames, logic steps and display refresh, JavaScript time per frame, JS heap, image
  cache, app memory, battery (level, charging, temperature), thermal status, renderer and
  graphics chip, clock and session time, and the HUD's own cost.
- HUD settings: presets (Minimal, Standard, Full), modules to tick and reorder by
  dragging, corner, size, opacity, compact or detailed layout, width at which the compact
  line wraps (50, 75 or 100 % of the screen), written-out or abbreviated labels (FPS,
  HEAP, BATT … as in common PC overlays), update rate, with a live preview. The ⓘ button
  explains every value. In a game, tap the HUD to switch between compact and detailed, and drag it
  wherever it is in the way least; on-screen controls on top of it keep priority.
### Changed
- Choice dialogs show a short description under each option and stay fully visible in
  landscape.
### Fixed
- The game's own renderer setting (for example in the Community_Basic plugin) could
  override the renderer the player chose.

## 0.3.3 – 2026-10-18
### Changed
- **New package id** `io.github.mircodhd.rpgmplayermv`: this version installs as a new app.
  Uninstall the old one and add your games again; saves in the game folders are kept.
- **No more all-files access.** Each game folder is shared through the system folder
  picker (read and write access to that folder only); session logs are written to
  `Documents/RPGM Player/logs/` without any permission.
- Short name **RPGM Player - MV**. The title stays in the top bar next to the settings
  button instead of scrolling away.
- Per-game settings no longer have separate "Use default" entries: the option that matches
  the global setting carries a **Default** tag, and choosing it follows the global setting
  again.
- On-screen controls: labels scale with their button and stay centred; the layout previews
  show opacity, size and left-handed mode live.
### Added
- Layout editor: size readout next to the selected control and a field to type the size,
  add and delete controls, rename button labels (per game only), and a toolbar that can be
  dragged out of the way.
- Switching haptic feedback on gives a short pulse, so you can feel it right away.
- "What's new" box after an update; also available under Settings → About.
- Export and import of settings: global settings, custom layouts and per-game settings.
- A small surprise in the settings for the curious.

## 0.3.2 – 2026-10-18
### Fixed
- Starting a game again after leaving one with "Quit game" no longer hangs on the loading
  screen until the app is closed completely. Leaving the game paused the JavaScript timers
  of the whole app, and the next game started with them still paused.

## 0.3.1 – 2026-10-18
### Changed
- Renamed the app from the working title "MV Player" to **RobustPlainGameMaker Player -
  MyVersion**; the launcher and the library show the short form **RPGM Player**. The
  package id is unchanged, so updates install over earlier versions.
- Session logs are now written to `Documents/RPGM Player/logs/`; logs from the old
  `Documents/MV Player/logs/` folder are still included when sharing logs.
- The "About" section shows the full app name with the version.
- APK files are named `RPGM-Player-<version>-<debug|release>.apk` instead of
  `app-<type>.apk`.
### Added
- Release APKs are signed.

## 0.3.0 – 2026-10-18 (Phase A: input)
### Added
- Two-level settings: global defaults and per-game overrides ("Use default" or an own
  value, reset per option), reachable from the game's info sheet and in game.
- Controller remapping for all game actions and the quick menu, globally or per game;
  several buttons and button combinations per action; conflict hint.
- On-screen gamepad with five generic presets (Classic Handheld, 16-bit Console, 6-Button
  Arcade, Modern Dual-Analog, Minimal): multi-touch, sliding d-pad, analog stick, haptics,
  opacity, size, left-handed mode; shown automatically when no controller is connected.
- Preset gallery with live previews and a layout editor (move, resize, hide, reset; save
  for one game or all games).
- In-game quick menu (Select + Start, back gesture or ☰ touch button): resume, on-screen
  controls, layout, frame rate, game settings, quit. Opening it pauses the game.
- Name entry scenes open the Android keyboard (native text field).
- Frame rate per game (60 fps or unlimited, marked experimental).
- Diagnostics: time spent on asset decryption in JavaScript per scene change.
- Unit tests for input routing, mapping, settings, presets, file index and decryption.
### Fixed
- Pausing for a menu and pausing the app no longer interfere with each other.

## 0.2.0 – 2026-10-17
### Added
- Material 3 interface (Jetpack Compose) with dynamic colours: game library with capsules
  showing the title screen image, folder, play time and a play button.
- Info sheet per game: MV and PIXI version, plugins, size, files, encryption, patch
  profiles; removing a game from the library (files stay untouched).
- Settings: 60 fps limit, diagnostic log opt-out, log sharing, version, developer link,
  license.
- Play time measured by the app.
- Licensed under GPL-3.0-or-later.

## 0.1.4 – 2026-10-17
### Fixed
- Play time ran twice as fast on 120 Hz displays (MV counts drawn frames): the frame rate
  is now limited to 60 fps by default.
### Changed
- Repeated identical log lines are collapsed; clearer frame statistics; version shown on
  the start screen.

## 0.1.3 – 2026-10-17
### Added
- "Share logs" exports all session logs as a zip.

## 0.1.2 – 2026-10-17
### Added
- Detailed diagnostic logging to session files (device, lifecycle, controller, audio,
  scenes, saves, errors, periodic performance statistics).

## 0.1.1 – 2026-10-17
### Fixed
- Game audio could stay muted after the app had been in the background (e.g. while
  pairing a controller).

## 0.1.0 – 2026-10-17
### Added
- First version: runs RPG Maker MV games from a folder in a fullscreen WebView, with the
  game files served locally (no internet permission), small compatibility patches,
  PC-compatible save files, native controller support and a Steam stub.
