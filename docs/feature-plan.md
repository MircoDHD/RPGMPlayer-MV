# Feature plan: input, display, compatibility, saves (0.3 – 0.6)

Status: approved 2026-10-18. **Phase A implemented in 0.3.0**; Phase B split into three releases: B1 renderer + B4 HUD **in 0.4.0**, then B2 frame rates, then B3 scaling and filters; C–D pending.
Base: version 0.2.0. Architecture: `docs/analysis.md`, part 2.

Size estimates are rough lines of new/changed code (Kotlin + JS + GLSL), excluding tests.

---

## 0. Cross-cutting foundations (done first, inside Phase A)

### 0.1 Two-level settings model
**Data model**
- `settings/GlobalSettings.kt`: all global defaults (replaces `PlayerSettings`; the frame
  cap preference is migrated). Stored as JSON in `filesDir/settings.json`.
- `settings/GameSettings.kt`: per-game overrides, every field nullable (`null` = "Use
  default"). Stored as `filesDir/game-settings/<gameId>.json` (separate files keep
  `library.json` small and avoid rewriting it on every slider change).
- `settings/SettingsResolver.kt`: `effective(global, game) -> EffectiveSettings`, the only
  object `GameActivity` and the patches see. Pure Kotlin → unit-tested.
- Option metadata (`settings/Options.kt`): key, type (bool/enum/int range/float range),
  default, flags `experimental` and `changesGameplay`, string resources. The UI renders
  settings from this metadata, so new options need no new screen code.
- `DebugSettings` stays separate (developer/diagnostics), unchanged.

**UI**
- Global `SettingsScreen` gains sections that mirror the per-game options.
- New `GameSettingsScreen` (from a "Game settings" button in `GameInfoSheet`):
  ```
  ┌ LargeTopAppBar: "Game settings · Fear & Hunger"  [Reset all]
  │ Section "Input":   Controller mapping ............ Default ▸
  │                    On-screen controls ............ Auto (default) ▾
  │ Section "Display": Renderer ...................... WebGL (default) ▾
  │                    Frame rate .................... 60 fps (default) ▾
  │                    Filters ....................... Off ▸
  │ Section "Compatibility": ...
  └ each row: value chip "Default: X" or the explicit value + "Reset" icon
  ```
  Experimental options carry an "Experimental" `AssistChip` and a one-line risk note;
  gameplay-changing ones a "Changes how the game plays" chip and are off by default.

**Hooks:** `GameActivity.startGame` (config JSON gets the effective settings),
`LibraryViewModel` (load/save), `GameInfoSheet` (entry point).
**Risk:** low. **Size:** ~600.

### 0.2 Native overlay host in GameActivity
`GameActivity`'s `FrameLayout` gets a `ComposeView` above the WebView: one Compose tree
for the virtual gamepad (A2), quick menu (A4), text input dialog (A3), HUD (B4) and
snackbars (C4). The WebView keeps focus for keyboard input; the overlay consumes only
touches inside its controls.
**Risk:** touch pass-through and z-order with the WebView surface; immersive mode with
dialogs. **Size:** ~200.

### 0.3 Input pipeline refactor
Today: `GamepadInput` → bitmask → `MvPlayer.setPad`. New:
```
hardware KeyEvent/MotionEvent ─► GamepadInput (physical button ids)
                                      │  ControllerMapping (A1)
on-screen controls (A2) ─────────────►│  logical actions
                                      ▼
                        InputRouter: MV actions → standard-gamepad bits → MvPlayer.setPad
                                     app actions → quick menu, (later fast-forward)
```
MV actions use the canonical bits that `mv/input.js` already maps (ok 0, cancel 1,
shift 2, menu 3, pageup 4, pagedown 5, up 12, down 13, left 14, right 15), so **the JS
side stays unchanged**. Pure Kotlin → unit-tested (`InputRouterTest`, `ControllerMappingTest`).
**Size:** ~300.

### 0.4 Test and review infrastructure
- Add JVM unit tests (JUnit 4, `app/src/test/`) for all pure logic: settings resolution,
  mapping, preset parsing, path normalisation, mojibake lookup, lenient JSON, decryption,
  range parsing. Gate: `./gradlew testDebugUnitTest assembleDebug lintDebug`.
- JS patches: smoke test in `tools/dev-server.py` with F&H and Termina after each patch
  change (boot to title, new game, save/load), including a fake-vsync harness for
  frame-timing code.
- At the end of each phase: a structured self code review (correctness, threading,
  lifecycle leaks, lint), fixes applied before the APK is delivered.

---

## Phase A — Input (0.3.0)

### A1. Hardware controller remapping
**Hooks:** `input/GamepadInput.kt` (emit physical ids instead of final bits),
new `input/ControllerMapping.kt`, `input/InputRouter.kt`, `GameActivity`.
**Data model:** `ControllerMapping = Map<MvAction|AppAction, Set<PhysicalButton>>`,
`PhysicalButton` = Android keycode or axis-direction (`HAT_X-`, `AXIS_Y+`, `LTRIGGER`…).
Global mapping in `settings.json`, per-game override in the game settings file.
Default mapping = today's behaviour (incl. Start → menu, d-pad from keys/hat/left stick).
**UI** (global: Settings → "Controller"; per game: Game settings → Input):
```
┌ "Controller"                                  [Reset to default]
│ Connected: Razer Kishi V3 Pro
│ ElevatedCard list:  OK ............ [A]          (tap → capture dialog)
│                     Cancel ........ [B]
│                     Menu .......... [Y] [Start]
│                     Dash/Shift .... [X]
│                     Page up/down .. [L1] / [R1]
│                     Up/Down/Left/Right [D-pad ↑] …
│                     Quick menu .... [Select]+[Start]
└ Capture dialog: "Press a button for OK…" (cancel / clear / timeout 10 s)
```
Capturing uses a dedicated `MappingCapture` mode of `GamepadInput` (also reachable from
the quick menu while a game runs). Conflicts (same button on two actions) are shown and
allowed only for directions + menu combos.
**Select + Start** → quick menu (A4) instead of the quit dialog.
**Risks:** controllers that send both key and axis events for the d-pad (handled by
de-duplicating per action); BACK key from controllers. **Test:** Kishi V3 Pro: remap
OK/Cancel, swap A/B, map d-pad to left stick only; verify in F&H menus and battle.
**Size:** ~600.

### A2. Virtual gamepad (touch overlay) with presets
**Hooks:** overlay host (0.2), `input/InputRouter.kt`, new `overlay/` package:
`GamepadPreset.kt` (data), `PresetRepository.kt` (assets + user files),
`VirtualGamepad.kt` (Compose), `LayoutEditor.kt`.
**Data model:** presets as JSON in `assets/gamepad/presets/*.json`:
```json
{ "id": "console16", "nameRes": "preset_console16", "version": 1,
  "controls": [
    { "id": "dpad", "type": "dpad", "anchor": "bottomStart", "x": 0.06, "y": 0.10,
      "size": 0.30, "actions": ["up","down","left","right"] },
    { "id": "a", "type": "button", "shape": "circle", "label": "A", "anchor": "bottomEnd",
      "x": 0.06, "y": 0.18, "size": 0.11, "action": "ok" } ] }
```
Coordinates are fractions of the safe area relative to an anchor (survives aspect-ratio
changes and display cutouts). Types: `dpad`, `stick`, `button`, `pill` (Start/Select),
`shoulder`. Custom layouts = same format in `filesDir/gamepad/custom/<id>.json`, flagged
`basedOn`, scoped global or per game.
Presets: *Classic Handheld*, *16-bit Console*, *6-Button Arcade*, *Modern Dual-Analog*,
*Minimal*: generic names, letters/neutral icons, colours from the app theme; no console
brands, logos or proprietary symbols.
**Behaviour:** multi-touch via `pointerInput` per control; d-pad uses angle sectors so
sliding between directions works without lifting (8-way with diagonals); stick has a
dead zone and maps to directions (MV is digital); optional haptics via
`View.performHapticFeedback` (no VIBRATE permission). Visibility Auto/Always/Never
(Auto follows the input-device listener that already exists). Opacity, size scale,
haptics, left-handed mirror.
**UI:** Settings → "On-screen controls": mode, opacity/size sliders, haptics, mirror, and a
preset gallery (horizontally scrolling `ElevatedCard`s with a live mini preview).
"Customize" opens the editor: game screenshot (PixelCopy of the window) dimmed, or a
neutral background outside a game; drag, pinch-resize, hide per control, reset; save as
"Custom (based on …)" globally or for this game.
**Risks:** biggest UI piece; WebView must still receive touches outside controls
(F&H/Termina disable touch anyway, other games may not); performance of a full-screen
Compose overlay (keep static layers, recompose only pressed state).
**Test:** each preset on the OnePlus 15 in landscape, sliding d-pad in F&H corridors,
simultaneous direction + dash, auto-hide on Kishi connect/disconnect.
**Size:** ~1500.

### A3. Text input (soft keyboard)
**Hooks:** new `mv/textinput.js` (in `mv-base`): wraps `Scene_Name.prototype.start`
(vanilla MV) and calls `MvPlayer.host.requestTextInput(currentName, maxLength)`; the
host shows a Compose dialog with an `OutlinedTextField` (IME opens), then calls
`MvPlayer.onTextInput(text|null)` which sets the edit window's name and triggers
`onInputOk` (or keeps the on-screen grid on cancel). Works for any MV game because it
hooks the engine scene; plugin-specific adapters (e.g. `DK_Name_Input`, which listens to
`keypress`) go into a small profile patch only if testing shows a conflict.
Max length and the game's character restrictions are enforced natively.
**Risks:** plugins that replace `Scene_Name` entirely; IME in immersive mode.
**Test:** F&H new game name entry (event 303), cancel path, max length, umlauts.
**Size:** ~300.

### A4. In-game quick menu
**Hooks:** overlay host, `InputRouter` app action, `onBackPressedDispatcher`,
lifecycle patch (`MvPlayer.onPause/onResume` reused: menu open = game paused, audio
suspended).
**UI:** `ModalBottomSheet` in landscape (or side sheet on wide screens), semi-transparent
scrim so the paused game stays visible (needed for B3 live preview):
```
Resume │ Controller layout ▸ (preset, overlay on/off) │ Frame rate ▸ │ Performance HUD ⏻
Display ▸ (from Phase B) │ Back up saves now (Phase D) │ Game settings ▸ │ Quit game
```
Entries for later phases appear only once implemented.
**Risks:** controller navigation inside the sheet (Compose focus handling with d-pad).
**Size:** ~400.

**Phase A total:** ~3900. Includes 0.1–0.4.

---

## Phase B — Display and performance (0.4.0)

### B1. Renderer choice (per game)
**Hooks:** `mv/engine.js` (`SceneManager.preferableRendererType`), new `mv/renderer.js`.
- WebGL (default). Canvas: forces `canvas` type; settings UI warns that Mode 7, lighting
  and filter plugins need WebGL.
- WebGL 2 (*experimental*): wrap `HTMLCanvasElement.prototype.getContext` for the game
  canvas only; `'webgl'`/`'experimental-webgl'` → try `'webgl2'` first. PIXI 4 is a WebGL 1
  renderer; WebGL 2 accepts GLSL ES 1.0 shaders but some extensions become core (VAO)
  and return null. If `PIXI.WebGLRenderer` throws or the first frame logs GL errors,
  reload once with WebGL 1 and record the fallback.
- Log the context actually created (`webgl`, `webgl2`, `2d`) and the GPU string.
**Risk:** low for WebGL/Canvas, medium for WebGL 2 (little expected benefit; kept
experimental). **Size:** ~150.

### B2. Frame rate options
Reuse `mv/framerate.js` and `applyFrameRateCap`: 60 (default), 30 (battery saver; game
logic still 60 steps/s, so motion gets choppier but play time stays correct because MV
counts rendered frames → **needs a play-time correction**: at 30 fps rendered frames
are half; the patch adds the skipped frames to `Graphics.frameCount`), Unlimited
(*experimental*, warning about doubled play time on 120 Hz).
**Size:** ~100.

### B3. Scaling, filters and post-processing
**Architecture** — one module `assets/patches/mv/postfx.js` + `assets/shaders/*.glsl`:
1. Patch `Graphics.render`: PIXI renders the stage into a `PIXI.RenderTexture` at the
   internal resolution (e.g. 816×624).
2. The canvas backing store is resized to **output pixels** (CSS size × devicePixelRatio)
   by patching `Graphics._updateCanvas`/`_updateRenderer`; CSS size and `_realScale`
   stay as today, so input mapping is unaffected.
3. A present sprite with the render texture is drawn into the output rect (scaling mode)
   with a chain of `PIXI.Filter`s (GLSL ES 1.0, works on WebGL 1 and 2): scaler → AA →
   effects → colour. Effects are combined into one "uber" pass where possible to keep
   the pass count low.
4. **No filter selected → none of this is installed** (zero overhead, today's path).
5. Canvas renderer: only scaling modes (CSS `image-rendering` + size), filters disabled
   with an explanation.
`SceneManager.snap`/`Bitmap.snap` keep working (they render the stage separately).

**Scaling modes:** Fit (default), Integer, Stretch; letterbox colour black or theme
surface colour.

**Filters and sources (only GPL-3.0-or-later-compatible; licenses checked in the source
files on 2026-10-17):**

| Filter | Source | License | Plan |
|---|---|---|---|
| Nearest, Bilinear | own | – | texture sampling modes |
| Sharp bilinear | libretro `interpolation/shaders/sharp-bilinear.glsl` (Themaister) | public domain | port or own (trivial) |
| Bicubic | own (Catmull-Rom / Mitchell-Netravali, published formulas) | – | own; libretro `cubic/bicubic.glsl` is GPL-2.0-or-later as fallback reference |
| Lanczos | own (published windowed-sinc formula) | – | libretro lanczos files carry **no license header → not used** |
| xBR (lv2) | libretro `xbr/shaders/xbr-lv2.glsl` (Hyllian) | MIT | port, *experimental* |
| ScaleFX | libretro `scalefx/shaders/scalefx-pass0..4` (Sp00kyFox) | MIT | port, *experimental*, 5 passes, likely "high" cost |
| FXAA | libretro `fxaa.glsl` is © NVIDIA "All rights reserved" without a license grant → **not used** | – | own clean-room implementation from the published FXAA approach (luma edge detection, sub-pixel blend) |
| SMAA | iryoku/smaa (Jimenez et al.) | MIT | optional, only if the measured cost is acceptable (3 passes + lookup textures) |
| MSAA | WebGL `antialias: true` | – | only if a visible difference is measurable (affects geometry edges, not sprites) |
| CAS sharpening | AMD FidelityFX CAS | MIT | port to GLSL ES 1.0 |
| CRT lite | libretro `crt/shaders/crt-lottes.glsl` | public domain | port |
| CRT full | libretro `crt/shaders/crt-geom.glsl` (cgwg, Themaister, DOLLS) | GPL-2.0-or-later | port (curvature, mask, glow) |
| Scanlines | own | – | |
| LCD grid | own; libretro `zfast_lcd.glsl` (GPL-2.0-or-later) as reference | – / GPL-2.0+ | `lcd-grid-v2.glsl` has **no license header → not used** |
| Vignette, film grain | own | – | |
| Brightness, contrast, gamma, saturation | own | – | one colour pass |
| Colour-blind simulation/correction | own; matrices from Machado, Oliveira & Fernandes (2009, published data), daltonization approach | – | |

Ported files keep their copyright/license headers, are marked as modified, and are listed
in `THIRD_PARTY.md` with repository, commit hash and license. GPL-2.0-or-later code is
used under GPL-3.0 (allowed by "or later").

**Filter presets:** Off, Pixel perfect (integer + nearest), Smooth (bilinear),
Sharp & clean (sharp bilinear + CAS), Retro CRT (crt-lottes or crt-geom), Handheld LCD.

**Cost measurement:** the developer cannot run the device, so the app gets a built-in
**filter benchmark** (Game settings → Display → "Measure filters"): on a paused frame it
draws each filter N times per frame with `gl.finish()` fencing and records the extra ms
at 60 Hz and native 120 Hz output; results are stored per device and shown as
low (< 1 ms) / medium (1–3 ms) / high (> 3 ms) next to each filter, and written to the
log so I can review the numbers.

**UI:** Display section in Game settings and in the quick menu with live preview (game
paused behind a scrim; changes apply immediately via `MvPlayer.postfx.configure(...)`):
```
Scaling: [Fit][Integer][Stretch]   Letterbox: [Black][Theme]
Scaler:  Nearest · Bilinear · Sharp bilinear · Bicubic · Lanczos · xBR (exp.) · ScaleFX (exp.)   [cost chip]
Anti-aliasing: Off · FXAA · SMAA (opt.)
Effects: Scanlines ⏻ ━━●━━  CRT lite/full ⏻  LCD grid ⏻  Sharpen (CAS) ⏻  Vignette ⏻  Grain ⏻
Colour: brightness · contrast · gamma · saturation · colour-blind mode ▾
Presets row (pill buttons): Off · Pixel perfect · Smooth · Sharp & clean · Retro CRT · Handheld LCD
```
**Risks:** largest technical risk of the block: GPU cost on 120 Hz output, interaction
with plugins that touch the renderer (TerraxLighting, FilterController, UltraMode7),
render-texture memory, context loss. Mitigation: zero-overhead default, per-game opt-in,
benchmark, automatic disable + log on GL errors.
**Test:** every preset in F&H (Terrax lighting maps) and Termina (UltraMode7 maps),
screenshots before/after, benchmark log review.
**Size:** ~2200 (incl. shaders and UI).

### B4. Performance HUD (modular)
**Hooks:** `mv/debug.js` already measures fps, frame gaps, logicUps, heap, image cache,
audio; it gets a `host.reportStats(json)` call at the configured sampling interval
(default 500 ms) in addition to the 10 s log snapshot. Native sources:
`BatteryManager` (level, charging, temperature from `ACTION_BATTERY_CHANGED`),
`PowerManager.currentThermalStatus`, app memory (`Debug.getMemoryInfo`/PSS, sampled at
low frequency), clock, session play time (from `GameActivity`).
**Design:** `hud/HudModule` interface (`id`, `title`, `requiredSources`, `@Composable
render(data, mode)`), `hud/HudRegistry` lists modules; a `HudDataHub` collects samples
from all sources into one immutable snapshot per tick. Adding a module = one new class +
registry entry, no changes elsewhere. Modules: FPS (current/avg), frame time + rolling
graph (Canvas-drawn sparkline), 1 % low + slow frames, logicUps + display Hz, JS heap,
image cache/audio buffers, app RAM, battery, thermal, renderer + filter chain + cost,
clock + session time.
**Layout:** corner, size S/M/L, opacity, compact/detailed, presets Minimal/Standard/Full,
module order via drag list in settings. Drawn natively in the overlay → unaffected by
filters/scaling, never in game screenshots. The HUD measures its own update+draw time
and shows it in "Full" mode; target < 0.5 ms per frame (updates only on new samples,
not every frame).
**Size:** ~900.

### B5. Native decryption (evaluate first)
1. Instrument in `mv/debug.js`: wrap `Decrypter.decryptArrayBuffer` and the image/audio
   decrypt paths to sum time per scene change/map transfer; log totals.
2. You send a log (map transfers in F&H/Termina); I report numbers.
3. Only if the gain is relevant: generalise the decrypted-twin path to serve every
   `.rpgmvp/.rpgmvo/.rpgmvm` decrypted, and set `Decrypter.hasEncryptedImages/Audio`
   false for the engine. (Note: native decryption also moves work off the JS thread.)
The instrumentation ships early (with 0.3.0) so data is ready for Phase B.
**Size:** ~100 for evaluation, ~150 if implemented.

**Phase B total:** ~3500.

---

## Phase C — Compatibility (0.5.0)

### C1. Patch profile system v2
**Hooks:** `patch/PatchProfiles.kt` → data-driven: `assets/patches/profiles.json`
describing profiles with match rules: game title (System.json), plugin name, plugin
version/hash (SHA-256 of the plugin file), engine version. "Well-known plugin fixes" =
small files in `assets/patches/fixes/` activated by plugin fingerprint.
**User patches:** optional `<game>/mvplayer-patches/patches.json`:
```json
{ "patches": [ { "file": "fix-font.js", "position": "beforePlugins" },
               { "file": "after.js", "position": "afterPlugins" } ] }
```
Positions: `beforeEngine`, `beforePlugins`, `afterPlugins` (new: injected before
`main.js`). Files served from that folder via `/__mvplayer/user/...`. Active profiles,
fixes and user patches listed in `GameInfoSheet`. Format documented in `docs/patches.md`.
Folder inside the game directory keeps patches portable with the game (proposal; an
alternative app-documents location is possible).
**Risk:** user patches run with full game privileges (same as plugins); clearly labelled.
**Size:** ~500.

### C2. Robust path and file lookup
**Hooks:** `GameRequestHandler.normalize`, `FileIndex`.
- Always: backslashes → `/`, collapse `//`, percent-decoding (already by `Uri.getPath`),
  Unicode NFC normalisation on both index keys and request paths.
- *Per game, default off:* mojibake fallback map: for each indexed name, add variants as
  if UTF-8 bytes had been decoded as CP437/Windows-1252, and Shift-JIS/GBK bytes as
  CP437/1252 (the typical zip-extraction mistakes), plus the reverse; lookup only after
  exact and lowercase misses; every hit logged.
**Size:** ~400 (+ unit tests with real-world mojibake samples).

### C3. Lenient JSON loading
**Hooks:** `GameRequestHandler` for `data/*.json` (native, logged).
- Always strip a UTF-8 BOM (MV's `JSON.parse` fails on it).
- *Per game, default off:* tolerant repair (strip `//` and `/* */` comments outside
  strings, trailing commas), validated with `org.json`; every repair logged with file
  name and a summary.
**Size:** ~200.

### C4. Error handling
**Hooks:** new `mv/errors.js`.
- "Continue after errors" (default off): wrap `SceneManager.catchException`/`onError`:
  log full stack, show a native snackbar via `host.notifyError(...)`, skip
  `printError`/`stop` when the scene is still usable; repeated identical errors are
  throttled. Clearly labelled: may leave the game in a broken state.
- "Suppress alert dialogs" (default off): `window.alert` → log + snackbar.
- `debug.js` keeps logging the full error regardless of the options.
**Size:** ~200.

### C5. Font override
**Hooks:** SAF `OpenDocument` (font types), copied to `filesDir/fonts/<gameId>/`;
`GameRequestHandler` serves it under `/__mvplayer/font/...`; a small patch adds an
`@font-face` for `GameFont` (MV's family name from `fonts/gamefont.css`) pointing to the
override and calls `Graphics.loadFont`. Per-game setting with preview text.
**Size:** ~250.

**Phase C total:** ~1550.

---

## Phase D — Saves and convenience (0.6.0)

### D1. Save manager and resume points (revised 2026-10-18)
Decision (Mirco): everything the save manager creates lives in **app storage**, not in
the game folder, so it survives deleting and re-installing the game files.

**a) Resume points (own save system, independent of the game)**
- Purpose: when playing on the go, the player can stop at any moment without losing much
  progress, instead of walking to a save point. It is offered as a help next to the
  game's own saves; it does not replace or alter the in-game save system.
- Created **manually** (quick menu → "Create resume point", optional controller shortcut)
  and optionally automatically when the app goes to the background (setting, default off).
- Content: the same data MV writes for a save (`DataManager.makeSaveContents()` via
  `JsonEx`, LZString) plus a thumbnail (`Bitmap.snap`) and metadata (map, play time,
  timestamp), stored in `filesDir/resume/<gameId>/`. Loading uses MV's own
  `DataManager.extractSaveContents` + `Scene_Map` transfer, the same path as loading a
  normal save, so no engine internals are bypassed.
- Kept separate from the game's save slots (never written to `www/save/`), N most recent
  kept (configurable, default 3). UI: list with thumbnails in the save manager, "Resume"
  on the library card (see D2).
- Clearly labelled: resume points let you continue from anywhere; games designed around
  limited saving (e.g. Fear & Hunger) are easier with them. Off unless the player uses it.

**b) Backups of the game's own saves**
- Before each overwrite through `PlayerBridge.saveWrite`, the old file is copied to
  `filesDir/save-backups/<gameId>/<file>.<timestamp>` (rolling, configurable count,
  default 5).
- Save manager screen (GameInfoSheet and quick menu): slots from `global.rpgsave`
  (decoded natively; LZString port in Kotlin, unit-tested) with timestamp, play time,
  character names; restore with confirmation (current file backed up first); export all
  saves as zip (PC-compatible names); import from zip (only known save names accepted).
- Warning text: restoring backups can bypass a game's intended difficulty; it is the
  user's choice.
**Size:** ~1100.

### D2. Continue button
**Hooks:** `GameEntry`/library card ("Continue" secondary pill next to play),
`GameActivity` extra `EXTRA_CONTINUE`, new `mv/continue.js`: after `Scene_Boot` is ready,
`DataManager.loadGame(DataManager.latestSavefileId())`, `reloadMapIfUpdated()`,
`SceneManager.goto(Scene_Map)`, falling back to the normal title on failure.
Per-game setting (on/off), shown only when saves exist.
**Risk:** games that initialise state on the title or pre-title (F&H uses
HIME_PreTitleEvents) → tested per game; profile can disable it.
**Size:** ~250.

### D3. Optional extras (not planned now; ask first)
Fast-forward, debug panel, mod overlay folder, haptics on screen shake.

**Phase D total:** ~1050.

---

## Versions and deliveries

| Version | Content |
|---|---|
| 0.3.0 | Phase A: settings model, overlay host, input refactor, A1–A4, decryption timing instrumentation |
| 0.4.0 | Phase B: renderer, frame rates, post-processing + benchmark, HUD, B5 decision |
| 0.5.0 | Phase C: profiles v2 + user patches, path/JSON robustness, error options, font override |
| 0.6.0 | Phase D: save manager with resume points and backups (app storage), continue button |
| 0.x.y | Bug-fix deliveries in between (patch digit) |

Each delivery: unit tests + build + lint green, dev-server smoke test (F&H, Termina),
self review, APK, a short German test checklist, commit + push.

## Test plan on the OnePlus 15 (per phase)
- **Always (regression):** F&H: title, new game incl. name entry, intro movie, load,
  save, map transfers, battle, audio after pause/resume, controller; Termina: boot to
  title, UltraMode7 map, video, save. Log shared after each session.
- **A:** remapping (swap A/B, stick-only), every overlay preset, layout editor, auto-hide
  with Kishi, IME name entry, quick menu with controller and touch.
- **B:** renderer switch incl. WebGL 2 fallback, 30/60/unlimited with play-time check,
  each filter preset + benchmark log, HUD presets and its own cost.
- **C:** a third MV game (if provided) with case/mojibake/JSON issues, user patch sample,
  error options with a deliberately broken patch, font override.
- **D:** resume point while walking in F&H → quit app → resume; backups on overwrite,
  restore, zip export → load on PC, continue button in F&H and Termina.
