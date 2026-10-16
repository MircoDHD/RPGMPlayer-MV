# MV Player (working title)

An ad-free, tracking-free Android player for games made with RPG Maker MV, run from a
game folder on the device. Early development; see `CLAUDE.md` for the plan.

No game files are included. You need your own copy of a game.

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

Debug builds allow remote debugging through `chrome://inspect`. Logs:
`adb logcat -s GameConsole GameRequest GameInspector PlayerBridge GameActivity`.

## Testing patches on the desktop

`tools/dev-server.py` mimics the app's request handling and save bridge, so patches can
be tried in a desktop browser. The game folder is only read; saves go to a temp folder.

```bash
tools/dev-server.py "/path/to/game" --port 8765
```

Controller input can be simulated in the browser console, e.g. `MvPlayer.setPad(1)`
(A pressed) and `MvPlayer.setPad(0)`.
