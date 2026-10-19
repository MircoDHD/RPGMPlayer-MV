# Technical notes

Part 1 analyses the two games the player was first built against (Fear & Hunger and
Fear & Hunger 2: Termina, both Windows builds on NW.js); the findings drive the
patch profiles in `app/src/main/assets/patches/`. Part 2 describes how the player works
and the pitfalls found while building it.

# Part 1: test games

## Overview

| | Fear & Hunger | Fear & Hunger 2: Termina |
|---|---|---|
| Engine | RPG Maker MV 1.6.0 (core lightly modified) | RPG Maker MV 1.6.0 (core lightly modified) |
| PIXI | 4.5.4 | 4.7.1 |
| `www/` size | 838 MB | 1.5 GB |
| Images (`.rpgmvp`) / audio (`.rpgmvo`) | 2074 / 433 | 3341 / 845 |
| Movies | 1 × `.webm` | 5 × `.webm` |
| Plugins enabled / listed | 48 / 57 | 83 / 111 |
| `.m4a` / `.mp4` shipped | none | none |

Layout: `Game.exe` + NW.js runtime, `www/` with `index.html`, `js/`, `data/`, `img/`,
`audio/`, `movies/`, `fonts/`. Saves live in `www/save/` (`fileN.rpgsave`,
`global.rpgsave`, `config.rpgsave`, LZString base64). Junk that the file index skips:
`__MACOSX/`, `.DS_Store`; also present but unused: `.psd`, `.xmp`, `js_copies/`.

## Encryption

Images and audio are encrypted (`hasEncryptedImages`/`hasEncryptedAudio` in
`System.json`, 32-hex-digit key). All files share the standard header
`RPGMV\0\0\0 00 03 01 ...`. No `.rpgmvm`. `img/system/Window.png` and `Loading.png` are
deliberately unencrypted (Decrypter ignore list / loaded directly by Graphics).

## Node / NW.js / Steam usage

- **Fear & Hunger: `Archeia_Steamworks` is enabled** and calls
  `require("./js/libs/greenworks")` at load time; about 200 plugin commands
  `steamworks activateachievement ...` exist in the data. Handled by the
  `greenworks-stub` profile (global `require` that only returns a no-op greenworks
  module; `initAPI()` returns false). Termina does not use Steam.
- YEP plugins call `require('nw.gui')` only in test mode or in version-check error
  branches.
- The core uses `fs`/`path`/`process` only in `StorageManager` local mode (NW.js).
  Replaced by the host save bridge.

## Effects of `Utils.isMobileDevice()` (true in Android WebView)

- `AudioManager.audioFileExt` -> `.m4a` and `Game_Interpreter.videoFileExt` -> `.mp4`:
  neither exists, so both are overridden based on the files actually shipped.
- `SceneManager.shouldUseCanvasRenderer` -> Canvas: would break Termina's `UltraMode7`
  (needs WebGL) and slow down lighting/filters. Overridden to allow WebGL.
- `Graphics._videoUnlocked` is false until a touch. Forced true (WebView allows autoplay).
- Kept on purpose: stretch-to-fit and mute-while-hidden.
- `YEP_KeyboardConfig` disables itself on mobile (acceptable).

## Other findings

- `SceneManager.isGameActive` does not exist in MV 1.6.0; no focus patch needed.
- Case mismatches exist (`penance1_1` vs `Penance1_1.rpgmvp`, `occultist1_1` vs
  `Occultist1_1.rpgmvp`), so the case-insensitive index is required. No two files
  differ only by case.
- Many file names contain spaces, `(`, `)` or `$` (354 / 736); URLs must be decoded.
- 10 / 64 database entries reference files that are not shipped (unused entries).
  Termina's `TY_AbortUrl` keeps missing images from stopping the game.
- `physical_attack_animation.js` throws a SyntaxError at load in both games
  (`eval(String(parameters))`); this happens on PC too and is left alone.
- Termina's `SRD_MenuBackgrounds` loads `menu.png` at plugin load time, before
  `System.json` enables decryption. PC builds recover through MV's retry; the player
  serves the decrypted `menu.rpgmvp` for such requests instead.
- Termina's `YSP_VideoPlayer` plays `.webm` files as PIXI textures (needs autoplay).
- Input: Termina disables touch (`YEP_TouchInputDisabler`), Fear & Hunger disables the
  mouse (`DisableMouse`). A touch overlay must therefore emit key/gamepad input.
- `DK_Name_Input` (keyboard name entry) needs the soft keyboard later.

# Part 2: how the player works

```
MainActivity (Compose)          GameActivity (fullscreen WebView, landscape)
  LibraryViewModel                 GameInspector -> FileIndex, GameInfo
  GameLibrary (library.json)       PatchProfiles -> PatchSet
  GameScanner (details, cover)     GameRequestHandler (shouldInterceptRequest)
  Settings screens                 PlayerBridge (@JavascriptInterface "MvPlayerHost")
                                   InputRouter, VirtualGamepad, GameOverlay (Compose)
PlayerApp: DebugLog init, crash handler      assets/patches/*.js injected into index.html
```

## Storage

- No storage permission. Each game folder is picked with `ACTION_OPEN_DOCUMENT_TREE` and
  kept with `takePersistableUriPermission` (read and write; released when the game is
  removed). The library stores the tree URI per game.
- `storage/GameNode` abstracts files: `DocumentNode` (Storage Access Framework,
  `DocumentsContract` queries) on the device, `FileNode` (`java.io.File`) in unit tests.
  Files are opened as real file descriptors, so seeking for range requests stays cheap.
- Listing a folder is one provider query, so the file index lists folders level by level
  with several queries in parallel (about 0.8 s instead of 1.5 s for 2830 files on an
  emulator; plain `java.io.File` needed about 0.15 s).
- Session logs go to `Documents/RPGM Player/logs/` with plain file APIs: since Android 11
  an app may create and manage its own files there without a permission.

## Serving a game (`web/GameRequestHandler.kt`)

- The game runs under the virtual origin `https://game.local/`; every WebView request goes
  through `shouldInterceptRequest`. Other hosts get 403. The app has no INTERNET
  permission; WebView metrics and Safe Browsing are disabled in the manifest.
- `game/FileIndex.kt` indexes the web root once: exact path lookup with a lowercase
  fallback (games made on Windows reference files with the wrong case). Skips
  `__MACOSX`, `.DS_Store` and `._*`.
- Encrypted assets are served as-is; MV's own JavaScript decrypts them. Single-range
  `Range` requests are answered with 206 (needed for `<video>`).
- `index.html` is rewritten: `window.MvPlayerConfig = {...}` and the "before engine"
  patches go before the first `<script>`, the "before plugins" patches before the
  `js/plugins.js` tag. Patches are served from `/__mvplayer/<path>`.
- Decrypted twin fallback: if `x.png/.ogg/.m4a` is requested but only the encrypted
  `x.rpgmvp/.rpgmvo/.rpgmvm` exists, the host decrypts it (`game/MvDecrypter.kt`: check
  the 16-byte `RPGMV` header, drop it, XOR the first 16 bytes with the System.json key).

## Game detection (`game/GameInspector.kt`)

- Web root: `<folder>/www` or `<folder>` itself, whichever contains `index.html`.
- Engine: `js/rpg_core.js` = MV, `js/rmmz_core.js` = MZ (not supported yet),
  `RPG_RT.ldb/lmt` = 2000/2003 (EasyRPG), `Game.ini` plus an RGSS archive or `Data` =
  XP/VX/VX Ace (mkxp-z).
- Read once per start: title, MV version (`Utils.RPGMAKER_VERSION`), PIXI version,
  plugins, whether a plugin uses greenworks, audio/video extension actually shipped,
  encryption key, title screen images (used as library cover).

## Patch layer (`patch/PatchProfiles.kt`, `assets/patches/`)

Profiles: `mv-base` (always) and `greenworks-stub` (if a plugin uses greenworks). Order
matters.

| File | Position | Purpose |
|---|---|---|
| `common/player.js` | before engine | `window.MvPlayer` namespace, logging through the bridge, global error hooks |
| `mv/perf.js` | before plugins, first | frame timing shared by the HUD and the log stats: measurement windows count rendered frames, animation frames and logic steps; reports samples to the host |
| `mv/renderer.js` | before plugins, before debug.js | renderer choice (see below) |
| `mv/debug.js` | before plugins | diagnostics: scenes and load times, errors, audio, video, map transfers, plugin commands, saves, decryption time, periodic stats (read from perf.js) |
| `mv/engine.js` | before plugins | audio/video extension from config; video unlocked; `window.close` exits the game |
| `mv/framerate.js` | before plugins | frame rate cap (see below); also times each main-loop iteration for the HUD |
| `mv/storage.js` | before plugins | `StorageManager` local mode through the save bridge |
| `mv/input.js` | before plugins | `Input._pollGamepads` fed by `MvPlayer.setPad(bitmask)` (standard gamepad layout) |
| `mv/lifecycle.js` | before plugins, after framerate.js | pause/resume with reasons (app paused, menu open): stops updates, suspends audio, pauses video, restores master gain |
| `mv/textinput.js` | before plugins | Android keyboard for `Scene_Name` |
| `stubs/greenworks.js` | before plugins | global `require()` that only returns a no-op greenworks |

`Utils.isMobileDevice()` is deliberately not faked; only the decisions it drives are
overridden (see part 1).

## Renderer (`mv/renderer.js`)

- WebGL (default) keeps PIXI's auto detection, i.e. WebGL with a Canvas fallback; on
  mobile MV itself would pick Canvas. Canvas forces PIXI's Canvas renderer. WebGL 2
  (experimental) gives the game canvas a WebGL 2 context: PIXI 4 is a WebGL 1 renderer,
  but WebGL 2 runs its GLSL ES 1.0 shaders. If no WebGL 2 renderer comes up, the canvas is
  replaced (a canvas that has one context type cannot get another) and WebGL 1 is used.
- The choice is enforced in `Graphics._createRenderer`, not only in
  `SceneManager.preferableRendererType`: plugins such as Community_Basic (shipped with MV)
  replace that function with the game's own setting.
- The renderer is fixed when the page starts; changing it during a game restarts the
  activity. The log records the requested and the created context, the GPU and GL errors
  after 30 frames. Fear & Hunger raises one `INVALID_OPERATION` (0x502) early on both
  WebGL 1 and 2, so that error alone says nothing about WebGL 2.

## Performance HUD (`hud/`)

- `mv/perf.js` sends a sample every 250/500/1000 ms through `MvPlayerHost.reportStats`
  while the HUD is on (nothing is measured or sent while it is off): fps, display
  refresh (animation frames), logic steps, average and maximum frame gap, 1 % low,
  slow frames (gap above two 60 Hz frames), JavaScript time per loop iteration, JS heap,
  image cache, renderer.
- Gaps longer than one second (pauses, loading) are ignored; after a pause the timing is
  reset so resuming does not count as a slow frame.
- JavaScript time per frame is measured in `framerate.js`'s loop, not by hooking
  `SceneManager.updateMain`: plugins such as TDDP_FluidTimestep replace that function.
- `HudDataHub` adds Android values at their own pace: battery from the sticky
  `ACTION_BATTERY_CHANGED` broadcast every sample, thermal status and headroom every 2 s
  (Android rate-limits the headroom), process memory (`Debug.getPss`) every 5 s on a
  background thread. App memory excludes the WebView renderer process, where the game runs.
- `HudRegistry` lists the modules; a module is one class (title, short tag, summary,
  detailed and compact text, optional graph). Labels are written out or abbreviated
  (upper-case tags as in MangoHud or the Steam Deck overlay; frame time follows fps
  without a tag); the settings show both forms per module and an info dialog explains
  every value. The panel is a Compose overlay above the game: it
  recomposes only when a sample arrives and is never part of game screenshots. A tap on
  the panel switches compact/detailed, dragging moves it (offset from the chosen corner,
  clamped so it stays on screen when its size changes). It is drawn below the on-screen
  controls, so they keep their touches. It measures its own update and draw time.
- Colours (`HudLevels`): frame rate, frame time and 1 % low are green up to 1.25 target
  frames, yellow up to two, red beyond, with the target taken from the frame rate cap
  (60 fps when uncapped). Other values have no green state; they turn yellow or red only
  near a limit, so the HUD stays calm when everything is fine.

## Saves (`bridge/PlayerBridge.kt`)

- JavaScript interface `MvPlayerHost`: `saveRead/saveWrite/saveExists/saveRemove(name)`,
  `log`, `exitGame`, `requestTextInput`. Calls are synchronous like MV's own API.
- Saves go to `<game>/www/save/` with the PC names and format (`fileN.rpgsave`,
  `global.rpgsave`, `config.rpgsave`, LZString base64), so they can be copied between PC
  and phone. The save folder is listed once per start and cached.
- Writes go to `<name>.tmp` first, then the old file is replaced by renaming, so a crash
  never destroys a save. Documents providers may rename new files (" (1)", extensions);
  `DocumentNode` checks the exact name. A name whitelist prevents path traversal.

## Input and on-screen controls

- The activity intercepts controller `KeyEvent`s and `MotionEvent`s before the WebView and
  maps them to physical control ids (`k<keycode>`, `a:<axis>:<±>`). `ControllerMapping`
  maps controls (and chords) to actions; `InputRouter` merges hardware and touch input
  and sends the standard-gamepad bitmask to `MvPlayer.setPad`. App actions (quick menu)
  are handled natively. `navigator.getGamepads()` is not used (unreliable in WebView).
- Some controllers report the d-pad as hat axes instead of key events; both are handled.
- On-screen layouts are JSON (`assets/gamepad/presets/*.json`, custom ones in the app's
  files). Positions and sizes are in units of the safe-area height relative to an
  anchor, so layouts keep their proportions on any aspect ratio; left-handed mode mirrors
  them. Each control handles its own pointers, so touches elsewhere still reach the game.
  Labels are sized from the control's box so they stay centred at any size.

## Settings

- `GlobalSettings` (`settings.json`) and per-game `GameSettings`
  (`game-settings/<id>.json`, every field nullable = follow the global value), resolved
  into `EffectiveSettings`. In the UI the option equal to the global value carries a
  "Default" tag; choosing it removes the override.
- Export/import writes global settings, custom layouts and per-game settings to one JSON
  file; games are matched by folder location or title, because library ids and folder
  grants do not survive a reinstall.

## Frame rate and play time

- MV runs its game logic at 60 steps per second but renders once per display refresh and
  counts rendered frames as play time (`Graphics.frameCount`, also stored in saves). On a
  120 Hz display the play time ran twice as fast (16 min 31 s shown after 8 min 20 s); at
  30 fps it would run at half speed.
- `mv/framerate.js` gates `requestAnimationFrame` to the chosen cap (30, 60 or none, with a
  quarter-interval tolerance) and the host asks Android for a matching refresh rate
  (`preferredRefreshRate`). MV's own accumulator keeps the logic at 60 steps per second.
- Play time: each `Graphics.render` adds the number of logic steps of the running scene
  since the previous render instead of 1 (1 at 60 fps, 2 at 30 fps, 1 or 0 at 120 Hz).
  `frameCount` still changes at most once per render, because code uses it as a "once
  per frame" marker (`Game_Interpreter.checkFreeze`, VE_BasicModule). Measured in the
  emulator: 15 s of play time in 15 s at 30, 60 and a simulated uncapped 120 Hz.
- 30 fps halves the GPU work; uncapped rendering above 60 draws repeated images unless a
  plugin interpolates between logic steps.

## Lifecycle pitfalls

- Pausing calls `webView.onPause()` and `pauseTimers()`. `pauseTimers()` is
  **process-wide**: if a game activity is finished while paused, the next WebView in the
  same process starts with frozen timers and the game hangs after loading its scripts.
  The activity therefore resumes timers when it creates the WebView and before
  destroying it.
- Audio could stay muted after the app was in the background: MV fades the master volume
  on `visibilitychange`; `lifecycle.js` disables that fade and restores the gain.
- Connecting a controller must not recreate the activity (broad `configChanges`).

## Diagnostics (`debug/`)

- Every line goes to logcat (tag `MvPlayer`) and, if enabled, to a session file. Identical
  lines repeating quickly are collapsed (first 5 per minute, then one summary line).
- Session header: app version, device model, Android version, WebView version, RAM,
  display; "Share logs" zips the session files through a `FileProvider`.
- JavaScript logs arrive as category `JS/<Category>`, console output as `Console`.
- Harmless noise in the test games: `CanvasTextAlign 'undefined'` (drawText without
  alignment) and a TerraxLighting plugin command issued every frame.

## Development tools

- `tools/dev-server.py "<game folder>" --port 8765 [--save-dir DIR]` mimics the app on the
  desktop (same injection, case-insensitive lookup, decrypted twins, bridge shim with
  synchronous saves), so patches can be tried in a desktop browser. Simulate controller
  input in the console with `MvPlayer.setPad(1)` / `MvPlayer.setPad(0)`. Keep its patch
  list and config in sync with `PatchProfiles.kt` and `GameActivity`.
- Browsers throttle `requestAnimationFrame` in hidden tabs; pump the loop manually
  (`setInterval(() => SceneManager.updateMain(), 16)`) when testing in the background.
- In the app's JavaScript, `chrome://inspect` works for debug builds (WebView remote
  debugging).
