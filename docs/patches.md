# Patches

RPGM Player - MV adds small JavaScript patches to every game. Most of them are built in;
a game can bring its own.

## The game's own patches

Put scripts into a folder named `mvplayer-patches` next to the game's `index.html` (for
games exported for Windows that is `www/mvplayer-patches/`). The folder travels with the
game, so the patches apply wherever the game folder is copied.

Without further files, every `.js` file directly in that folder runs **after the
plugins**, in alphabetical order. To choose the order and the moment, add a
`patches.json`:

```json
{
  "patches": [
    { "file": "early-fix.js", "position": "beforePlugins" },
    { "file": "after-plugins.js" }
  ]
}
```

| Position | Runs |
|---|---|
| `beforeEngine` | before any game script, even before `rpg_core.js` |
| `beforePlugins` | after MV's core files (`rpg_*.js`), before the first plugin |
| `afterPlugins` (default) | after the last plugin, before the game starts |

Only files listed in `patches.json` run when it exists. Entries pointing outside the
folder or to missing files are skipped and logged. The game's info card lists the
patches it found.

Patches run with the same rights as plugins: they can change anything in the game. Only
use patches you trust.

### Example

`www/mvplayer-patches/faster-text.js`, shows messages instantly:

```js
(function () {
    Window_Message.prototype.isFastForward = function () {
        return true;
    };
})();
```

## Built-in patches and profiles

The built-in patches live in `app/src/main/assets/patches/` and are grouped into profiles
in `assets/patches/profiles.json`. A profile applies when all of its `match` rules hold:

| Rule | Example | Holds when |
|---|---|---|
| `title` | `"hunger"` | the game title matches this regular expression (case-insensitive) |
| `plugins` | `["YEP_CoreEngine"]` | all these plugins are enabled |
| `pluginSha256` | `{"Foo": "3a7b…"}` | the plugin is enabled and `js/plugins/Foo.js` has exactly this SHA-256 |
| `engine` | `{"min": "1.5.0", "max": "1.6.2"}` | the MV version (`rpg_core.js`) is in this range |
| `usesGreenworks` | `true` | a plugin uses Steam's greenworks module |

A profile without `match` always applies. A fix for one known plugin version is a small
file in `assets/patches/fixes/` plus a profile matching that plugin's hash, so it never
touches other versions of the plugin.

## Stand-ins for the PC version

Plugins written for the PC version (NW.js) sometimes call `require('fs')`,
`require('path')`, `require('nw.gui')` or read `process` without checking
`Utils.isNwjs()`. `mv/nwjs.js` provides harmless stand-ins:

- `fs` reads the game's own files (paths as on PC: the working directory is the game
  folder, the page is in `www`); writing is refused without an error;
- `path` and `os` work on plain values; `nw.gui` window functions do nothing, closing the
  window quits the game;
- any other module throws `MODULE_NOT_FOUND`, as in Node.js.

`Utils.isNwjs()` stays `false`, so MV and well-behaved plugins keep their mobile
behaviour. Each stand-in that is used is logged once (`NwJs` in the log).
