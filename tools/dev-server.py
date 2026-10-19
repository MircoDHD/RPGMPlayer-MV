#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 MircoDHD
# SPDX-License-Identifier: GPL-3.0-or-later
"""Desktop test server that mimics the Android app's request handling.

Serves an MV game folder like GameRequestHandler does (case-insensitive lookup,
patches injected into index.html) and emulates the MvPlayerHost bridge, so the
JS patches can be debugged in a desktop browser. The game folder is only read;
saves go to --save-dir.

Usage: tools/dev-server.py "/path/to/game" [--port 8765] [--save-dir DIR]
Gamepad input can be simulated from the console: MvPlayer.setPad(1) / MvPlayer.setPad(0).
"""
import argparse
import json
import mimetypes
import re
import sys
import tempfile
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlparse

REPO = Path(__file__).resolve().parent.parent
PATCH_DIR = REPO / "app/src/main/assets/patches"
SAFE_NAME = re.compile(r"[A-Za-z0-9_\-][A-Za-z0-9_.\-]{0,127}")
MV_HEADER = bytes([0x52, 0x50, 0x47, 0x4D, 0x56, 0, 0, 0, 0, 3, 1, 0, 0, 0, 0, 0])
ENCRYPTED_EXTENSIONS = {"png": "rpgmvp", "ogg": "rpgmvo", "m4a": "rpgmvm"}

# Mirrors PatchProfiles.kt.
BEFORE_ENGINE = ["common/player.js"]
BEFORE_PLUGINS = ["mv/perf.js", "mv/renderer.js", "mv/debug.js", "mv/engine.js", "mv/framerate.js", "mv/storage.js",
                  "mv/input.js", "mv/lifecycle.js", "mv/textinput.js"]
GREENWORKS_PATCHES = ["stubs/greenworks.js"]

HOST_SHIM = """
window.MvPlayerHost = (function () {
    function call(method, name, body) {
        var xhr = new XMLHttpRequest();
        xhr.open(method, '/__dev/save/' + encodeURIComponent(name), false);
        xhr.send(body === undefined ? null : body);
        return xhr;
    }
    return {
        saveRead: function (name) { var x = call('GET', name); return x.status === 200 ? x.responseText : null; },
        saveWrite: function (name, data) { return call('PUT', name, data).status === 204; },
        saveExists: function (name) { return call('HEAD', name).status === 200; },
        saveRemove: function (name) { return call('DELETE', name).status === 204; },
        exitGame: function () { document.body.innerHTML = '<p style="color:#888">exitGame() called</p>'; },
        requestTextInput: function (current, maxLength) {
            // Asynchronous like the app's dialog.
            setTimeout(function () {
                var text = window.prompt('Name (max ' + maxLength + ')', current);
                MvPlayer.onTextInput(text);
            }, 0);
        },
        log: function (level, category, message) {
            var method = level === 'e' ? 'error' : level === 'w' ? 'warn' : 'log';
            console[method]('[' + level.toUpperCase() + ' ' + category + '] ' + message);
        }
    };
})();
"""


def build_index(root: Path):
    exact, lower = {}, {}
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        rel = path.relative_to(root).as_posix()
        if "__MACOSX/" in rel or path.name.lower() in (".ds_store", "thumbs.db") or path.name.startswith("._"):
            continue
        exact[rel] = path
        lower.setdefault(rel.lower(), path)
    return exact, lower


def find_web_root(game: Path) -> Path:
    for candidate in (game / "www", game):
        if (candidate / "index.html").is_file() and (candidate / "js/rpg_core.js").is_file():
            return candidate
    sys.exit(f"No RPG Maker MV game found in {game}")


def inspect(root: Path, exact, lower):
    def any_under(prefix, exts):
        return any(k.startswith(prefix) and k.endswith(exts) for k in lower)

    title, key = root.parent.name, None
    try:
        system = json.loads((root / "data/System.json").read_text(encoding="utf-8-sig"))
        title = system.get("gameTitle") or title
        if len(system.get("encryptionKey") or "") == 32:
            key = bytes.fromhex(system["encryptionKey"])
    except (OSError, ValueError):
        pass
    plugins = []
    try:
        text = (root / "js/plugins.js").read_text(encoding="utf-8-sig")
        plugins = [p["name"] for p in json.loads(text[text.index("["):text.rindex("]") + 1]) if p.get("status")]
    except (OSError, ValueError):
        pass
    uses_greenworks = any(
        "greenworks" in lower[f"js/plugins/{name}.js".lower()].read_text(errors="replace").lower()
        for name in plugins if f"js/plugins/{name}.js".lower() in lower
    )
    has_ogg = any_under("audio/", (".ogg", ".rpgmvo"))
    has_m4a = any_under("audio/", (".m4a", ".rpgmvm"))
    has_webm = any_under("movies/", (".webm",))
    has_mp4 = any_under("movies/", (".mp4",))
    profiles = ["mv-base"] + (["greenworks-stub"] if uses_greenworks else [])
    config = {
        "title": title,
        "audioExt": ".m4a" if (not has_ogg and has_m4a) else ".ogg",
        "videoExt": ".mp4" if (not has_webm and has_mp4) else ".webm",
        "profiles": profiles,
        "frameRateCap": 60,
        "renderer": "webgl",
        "hud": {"intervalMs": 0},
        "debug": {"verbose": True, "statsIntervalSeconds": 10},
    }
    before_plugins = BEFORE_PLUGINS + (GREENWORKS_PATCHES if uses_greenworks else [])
    return config, before_plugins, key


def decrypted_twin(rel, lookup, key):
    """Mirrors GameRequestHandler.serveDecryptedTwin."""
    ext = rel.rsplit(".", 1)[-1].lower()
    if key is None or ext not in ENCRYPTED_EXTENSIONS:
        return None
    twin = lookup(rel[: -len(ext)] + ENCRYPTED_EXTENSIONS[ext])
    if twin is None:
        return None
    data = twin.read_bytes()
    if data[:16] != MV_HEADER:
        return None
    body = bytearray(data[16:])
    for i in range(min(16, len(body))):
        body[i] ^= key[i]
    return bytes(body)


def make_handler(root, exact, lower, index_html, save_dir, key):
    class Handler(SimpleHTTPRequestHandler):
        def log_message(self, fmt, *args):
            # Request lines carry the status as second argument; hide the successful ones.
            if len(args) >= 2 and str(args[1]) in ("200", "204", "206"):
                return
            super().log_message(fmt, *args)

        def send_bytes(self, status, body: bytes, mime="application/octet-stream"):
            self.send_response(status)
            self.send_header("Content-Type", mime)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(body)

        def save_path(self, path):
            name = unquote(path[len("/__dev/save/"):])
            return save_dir / name if SAFE_NAME.fullmatch(name) else None

        def do_GET(self):
            path = urlparse(self.path).path
            if path.startswith("/__dev/save/"):
                target = self.save_path(path)
                if target and target.is_file():
                    return self.send_bytes(200, target.read_bytes(), "text/plain; charset=utf-8")
                return self.send_bytes(404, b"")
            if path in ("/", "/index.html"):
                return self.send_bytes(200, index_html, "text/html; charset=utf-8")
            if path == "/__mvplayer/dev/host-shim.js":
                return self.send_bytes(200, HOST_SHIM.encode(), "text/javascript")
            if path.startswith("/__mvplayer/"):
                patch = PATCH_DIR / path[len("/__mvplayer/"):]
                if patch.is_file() and PATCH_DIR in patch.resolve().parents:
                    return self.send_bytes(200, patch.read_bytes(), "text/javascript")
                return self.send_bytes(404, b"")
            rel = unquote(path).lstrip("/")
            lookup = lambda p: exact.get(p) or lower.get(p.lower())
            file = lookup(rel)
            if file is None:
                body = decrypted_twin(rel, lookup, key)
                if body is not None:
                    self.log_message("DECRYPTED TWIN for %s", rel)
                    return self.send_bytes(200, body, mimetypes.guess_type(rel)[0] or "application/octet-stream")
                self.log_message("NOT FOUND %s", rel)
                return self.send_bytes(404, b"")
            mime = mimetypes.guess_type(file.name)[0] or "application/octet-stream"
            return self.send_bytes(200, file.read_bytes(), mime)

        do_HEAD = do_GET

        def do_PUT(self):
            target = self.save_path(urlparse(self.path).path)
            if target is None:
                return self.send_bytes(400, b"")
            length = int(self.headers.get("Content-Length", 0))
            save_dir.mkdir(parents=True, exist_ok=True)
            target.write_bytes(self.rfile.read(length))
            return self.send_bytes(204, b"")

        def do_DELETE(self):
            target = self.save_path(urlparse(self.path).path)
            if target is None:
                return self.send_bytes(400, b"")
            target.unlink(missing_ok=True)
            return self.send_bytes(204, b"")

    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("game", type=Path)
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--save-dir", type=Path, default=Path(tempfile.gettempdir()) / "mvplayer-dev-saves")
    args = parser.parse_args()

    root = find_web_root(args.game)
    exact, lower = build_index(root)
    config, before_plugins, key = inspect(root, exact, lower)

    engine_tags = f"<script>window.MvPlayerConfig = {json.dumps(config)};</script>\n"
    engine_tags += '<script src="/__mvplayer/dev/host-shim.js"></script>\n'
    engine_tags += "".join(f'<script src="/__mvplayer/{p}"></script>\n' for p in BEFORE_ENGINE)
    plugin_tags = "".join(f'<script src="/__mvplayer/{p}"></script>\n' for p in before_plugins)
    html = (root / "index.html").read_text(encoding="utf-8")
    first_script = re.search(r"<script", html, re.I)
    html = html[:first_script.start()] + engine_tags + html[first_script.start():]
    plugins_tag = re.search(r"""<script[^>]*src\s*=\s*["']?js/plugins\.js""", html, re.I)
    html = html[:plugins_tag.start()] + plugin_tags + html[plugins_tag.start():]

    print(f"{config['title']}: {len(exact)} files, profiles {config['profiles']}")
    print(f"Saves: {args.save_dir}")
    print(f"Open http://localhost:{args.port}/")
    handler = make_handler(root, exact, lower, html.encode(), args.save_dir, key)
    ThreadingHTTPServer(("127.0.0.1", args.port), handler).serve_forever()


if __name__ == "__main__":
    main()
