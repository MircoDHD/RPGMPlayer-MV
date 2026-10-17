# MV Player (working title)

An ad-free, tracking-free Android player for games made with RPG Maker MV, run from a
game folder on the device. Early development; see `CLAUDE.md` for the plan.

No game files are included. You need your own copy of a game.

## Using it

Add a game with "Add game" and pick the folder that contains `Game.exe` or `index.html`.
The library shows each game with its title screen image, folder, play time and a play
button; the info button lists technical details (MV and PIXI version, plugins, size).
Settings hold the 60 fps limit, the diagnostic log opt-out, log sharing and app info.

## How it works

- The game folder is served to a WebView under `https://game.local/` from a
  case-insensitive file index; the app has no INTERNET permission.
- Small JS patches (`app/src/main/assets/patches/`) are injected into `index.html`,
  grouped into profiles (`PatchProfiles.kt`).
- Saves are written to the game's `www/save/` folder in the PC format, so they can be
  copied between PC and Android.
- Controllers are read natively and passed to the engine; Select + Start opens the quit
  dialog.

## Building

Requirements: JDK 17+ and the Android SDK.

```bash
scripts/setup-sdk.sh        # installs the SDK to ~/Android/Sdk, writes local.properties
./gradlew assembleDebug     # APK in app/build/outputs/apk/debug/
./gradlew installDebug      # install on a connected device
```

Debug builds allow remote debugging through `chrome://inspect`.

## Logs

Every session writes a log file to `Documents/MV Player/logs/` on the device (the last 15
are kept; before all-files access is granted, to the app's external files folder). The
same lines go to logcat: `adb logcat -s MvPlayer`. "Share logs" on the start screen packs
all session logs into a zip and opens the Android share sheet.

Logged: device, WebView and GPU details, game inspection and plugin list, lifecycle,
input devices and controller buttons (including unmapped keys), audio outputs, media
volume, thermal status, scene and map changes with load times, BGM/BGS/ME, plugin
commands, saves, load errors, JS errors, and a stats snapshot every 10 s (FPS, slow
frames, JS heap, image cache, audio state). Debug builds additionally log every served
file, sound effects, raw axis values and video element events. The switches live in
`DebugSettings` and are meant to get a settings screen later.

## Testing patches on the desktop

`tools/dev-server.py` mimics the app's request handling and save bridge, so patches can
be tried in a desktop browser. The game folder is only read; saves go to a temp folder.

```bash
tools/dev-server.py "/path/to/game" --port 8765
```

Controller input can be simulated in the browser console, e.g. `MvPlayer.setPad(1)`
(A pressed) and `MvPlayer.setPad(0)`.

## License

MV Player is free software: you can redistribute it and/or modify it under the terms of
the GNU General Public License as published by the Free Software Foundation, either
version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE).

It uses AndroidX and Jetpack Compose (Apache License 2.0). RPG Maker is a trademark of
its respective owners; this project is not affiliated with them. No game files are
included.
