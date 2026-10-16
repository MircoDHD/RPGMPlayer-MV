# Analysis of the test games

Roadmap step 1. Both games are Windows builds (NW.js). Findings drive the
patch profiles in `app/src/main/assets/patches/`.

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
