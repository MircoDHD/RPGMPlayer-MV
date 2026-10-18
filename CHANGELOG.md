# Changelog

All notable changes to RobustPlainGameMaker Player - MyVersion ("RPGM Player - MV").
Versions follow `major.minor.patch`: the minor digit for new features, the patch digit for
fixes. Dates are build dates.

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
