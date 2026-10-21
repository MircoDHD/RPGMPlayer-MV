# RobustPlainGameMaker Player - MyVersion

**RPGM Player - MV** for short: an ad-free, tracking-free Android player for games made with
**RPG Maker MV**. It runs the PC (Windows/NW.js) version of a game straight from a folder
on your phone, with controller support, on-screen controls and PC-compatible saves.

> [!WARNING]
> **Use at your own risk.** This is a hobby project, provided as is, without any
> warranty (see the [license](LICENSE)). Back up your save files before trying it.
>
> **Vibe-coded.** The app is developed with heavy help from an AI coding assistant
> (Claude) and tested on one phone. Expect rough edges.
>
> **Made for me first.** I build it to play my own games on my own device. Everyone is
> welcome to use it, report issues or contribute, but there is no support promise.

No games are included. You need your own copy of a game.

---

## Features

- **Library** of your games with title screen image, folder, play time and a play button;
  an info sheet per game shows the RPG Maker MV and PIXI version, plugins, size and more.
- **Plays the PC version unchanged**: the game folder is read in place, case-insensitive
  file lookup (games made on Windows), encrypted assets supported.
- **PC-compatible saves**: written to the game's own `www/save/` folder in the same format,
  so you can copy saves between PC and phone.
- **No storage permission**: you share each game folder once through Android's folder
  picker; the app sees only those folders.
- **Hardware controllers**, natively supported and **remappable** (globally or per game).
- **On-screen gamepad** with presets, a layout editor, opacity, size, haptics and a left-handed mode;
  shown automatically when no controller is connected.
- **Quick menu** in game (back gesture or ☰ button): pause, controls,
  layout, frame rate, game settings, quit.
- **Android keyboard** support.
- **Frame rate** 30, 60 (default) or the display rate, with the in-game play time
  correct at every setting (MV counts drawn frames as play time, so it used to run too
  fast on 90/120 Hz screens).
- **Renderer choice** (WebGL, Canvas, WebGL 2) globally or per game.
- **Picture**: fit, integer or stretched scaling; smooth, sharp or pixel enlargement;
  scanlines, sharpening, brightness, contrast and saturation, with a live preview in game.
- **Performance HUD**: frame rate, frame times with graph, memory, battery, temperature and
  more above the game, with modules you choose and arrange.
- **Per-game settings** and **export/import** of all settings.
- **Private**: no internet permission, no ads, no tracking, no telemetry. Diagnostic logs
  stay on the device and can be switched off.
- Material 3 design with dynamic colours, English and German.

## Status

Early development. Tested on a OnePlus 15 (Android 16) with a Razer Kishi V3 Pro.

| Game | Status |
|---|---|
| Fear & Hunger | ✅ Playable |
| Fear & Hunger 2: Termina | Starts (not tested further yet) |
| Other RPG Maker MV games | Should work, not tested |
| RPG Maker MZ | Not supported yet |
| RPG Maker XP / VX / VX Ace / 2000 / 2003 | Not supported (use mkxp-z or EasyRPG Player) |

## Requirements

- Android 11 or newer with an up-to-date Android System WebView.
- The **PC version of an RPG Maker MV game** (the folder that contains `Game.exe` and a
  `www` folder, or `index.html` directly). Encrypted games are fine.
- Free storage for the game folder (often several hundred MB to a few GB).
- Optional: a Bluetooth or USB game controller.

## Installation

1. **Download the APK** of the latest version from the
   [Releases page](../../releases) and install it.
   Your file manager or browser asks once for permission to install apps from that source.
2. **Copy the game folder** to the device and extract it to the desired location. 
   `Internal storage/Games/<Game name>/`. The `www` folder is what matters; `Game.exe` and
   the DLLs are not needed.
3. **Start the app**, tap **Add game** and choose the game folder (the one containing
   `www` or `index.html`), then confirm **Allow**.
4. Tap the **play button**. Use a controller or the on-screen controls; Select + Start or
   the back gesture opens the quick menu.

Saves from the PC version can be copied into `www/save/` of the game folder on the
phone, and the other way round.

## Roadmap

| Phase | Version | Content | Status |
|---|---|---|---|
| Basic player | 0.1 | Player, local file serving, compatibility patches, PC saves, controller | Done |
| Library | 0.2 | Material 3 library, info sheet, settings, play time | Done |
| Input | 0.3 | Controller remapping, on-screen gamepad + editor, quick menu, keyboard | Done |
| Display & performance | 0.4 | Renderer choice and modular performance HUD (0.4.0); 30/60/unlimited fps with correct play time (0.4.1); scaling and picture filters (0.4.4) | Done |
| Compatibility | 0.5 | Patch profiles and user patches, robust file names and JSON, error handling options, font override | Planned |
| Saves & convenience | 0.6 | Resume points (app-side quick saves to stop anywhere), save backups and export, continue button | Planned |
| Release | – | CI builds, F-Droid / IzzyOnDroid | Planned |
| Later | – | RPG Maker MZ support, optional Linux launcher | Ideas |

Changes per version: [CHANGELOG.md](CHANGELOG.md) and the [Releases page](../../releases).

## How it works

- The game folder is served to an Android WebView under the virtual address
  `https://game.local/`; every request is answered locally from an index of the folder.
- Small JavaScript patches are injected into the game's `index.html` to adapt RPG Maker
  MV to Android (audio/video formats, WebGL, saves, input, pausing). They live in
  `app/src/main/assets/patches/` and are grouped into profiles.
- Controllers and the on-screen gamepad are read natively and fed into the engine's own
  input system.

More technical detail: [docs/analysis.md](docs/analysis.md).

## Logs and privacy

- **No internet access:** the app has no internet permission and sends nothing anywhere.
  No ads, no tracking, no telemetry.
- **No storage permission:** the app can only open the game folders you pick yourself.
- **Diagnostic logs:** to help find bugs, session logs are written to
  `Documents/RPGM Player/logs/` (the last 15 sessions are kept).
- **What is logged:** device and WebView details, game info, app lifecycle, controller
  input, audio state, scene changes, errors and performance statistics.
- **Opt out:** switch logging off under **Settings → Privacy & diagnostics**.
- **Sharing is up to you:** **Share logs** packs the logs into a zip, e.g. to attach to a
  bug report.

## License

RPGM Player - MV is free software: you can redistribute it and/or
modify it under the terms of the GNU General Public License as published by the Free
Software Foundation, either version 3 of the License, or (at your option) any later
version. It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY.
See [LICENSE](LICENSE). Third-party components: [THIRD_PARTY.md](THIRD_PARTY.md).

RPG Maker is a trademark of its respective owners. This project is independent and not
affiliated with or endorsed by them. No game files are included.
