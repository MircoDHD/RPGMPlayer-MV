// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * RPGM Player: stand-ins for NW.js and Node.js. Runs after rpg_*.js, before plugins.
 * Patch version: 1
 *
 * Plugins written for the PC version call require('nw.gui'), require('fs'), require('path')
 * or read `process`, sometimes without checking Utils.isNwjs(). In a browser that throws
 * and leaves the plugin half-initialised. This provides:
 *  - require() for fs, path, os, nw.gui (and modules other patches register, e.g. the
 *    greenworks stub); anything else throws MODULE_NOT_FOUND like Node does;
 *  - `process` and `nw` globals with harmless values;
 *  - fs that READS the game's files (synchronous requests to the player, which is what the
 *    Sync functions need); writes are refused silently; paths map like on PC, where the
 *    working directory is the game folder and the page lives in its www folder.
 * Utils.isNwjs() stays false, so MV itself keeps its mobile behaviour. Every stand-in that
 * is used is logged once, so the log shows which plugin needed what.
 */
(function () {
    'use strict';

    var CWD = '/game';
    var WEB = CWD + '/www';
    var logged = {};

    function note(what) {
        if (logged[what]) return;
        logged[what] = true;
        MvPlayer.log('i', 'NwJs', 'Stand-in used: ' + what);
    }

    function noop(name) {
        return function () {
            note(name);
        };
    }

    /** Unknown members become logged no-ops instead of "undefined is not a function". */
    function lenient(target, name) {
        if (typeof Proxy !== 'function') return target;
        return new Proxy(target, {
            get: function (t, key) {
                if (key in t || typeof key === 'symbol' || key === 'then' || key === 'toJSON') return t[key];
                return noop(name + '.' + String(key));
            }
        });
    }

    // --- path (POSIX) ----------------------------------------------------------------------

    function normalize(p) {
        p = String(p).replace(/\\/g, '/');
        var absolute = p.charAt(0) === '/';
        var parts = [];
        p.split('/').forEach(function (part) {
            if (!part || part === '.') return;
            if (part === '..') {
                if (parts.length && parts[parts.length - 1] !== '..') parts.pop();
                else if (!absolute) parts.push('..');
            } else {
                parts.push(part);
            }
        });
        var result = (absolute ? '/' : '') + parts.join('/');
        return result || (absolute ? '/' : '.');
    }

    var path = {
        sep: '/',
        delimiter: ':',
        normalize: normalize,
        join: function () {
            return normalize(Array.prototype.slice.call(arguments).filter(function (p) { return p !== ''; }).join('/'));
        },
        resolve: function () {
            var result = CWD;
            Array.prototype.forEach.call(arguments, function (p) {
                p = String(p).replace(/\\/g, '/');
                result = p.charAt(0) === '/' ? p : result + '/' + p;
            });
            return normalize(result);
        },
        isAbsolute: function (p) {
            return String(p).replace(/\\/g, '/').charAt(0) === '/';
        },
        dirname: function (p) {
            p = normalize(p);
            var i = p.lastIndexOf('/');
            return i < 0 ? '.' : i === 0 ? '/' : p.slice(0, i);
        },
        basename: function (p, ext) {
            var base = normalize(p).split('/').pop();
            return ext && base.slice(-ext.length) === ext ? base.slice(0, -ext.length) : base;
        },
        extname: function (p) {
            var base = normalize(p).split('/').pop();
            var i = base.lastIndexOf('.');
            return i > 0 ? base.slice(i) : '';
        },
        relative: function (from, to) {
            var a = path.resolve(from).split('/').filter(Boolean);
            var b = path.resolve(to).split('/').filter(Boolean);
            while (a.length && b.length && a[0] === b[0]) {
                a.shift();
                b.shift();
            }
            return a.map(function () { return '..'; }).concat(b).join('/');
        }
    };
    path.posix = path;
    path.win32 = path;

    // --- fs (read-only) --------------------------------------------------------------------

    /** Path relative to the game's web root, or null outside the game folder. */
    function gamePath(p) {
        p = String(p).replace(/\\/g, '/').replace(/^file:\/\//, '');
        var absolute = path.resolve(p);
        if (absolute === WEB || absolute === CWD) return '';
        if (absolute.indexOf(WEB + '/') === 0) return absolute.slice(WEB.length + 1);
        // Games without a www folder: the page lives in the game folder itself.
        if (absolute.indexOf(CWD + '/') === 0) return absolute.slice(CWD.length + 1);
        return null;
    }

    function encodePath(rel) {
        return rel.split('/').map(encodeURIComponent).join('/');
    }

    function request(url, binary) {
        var xhr = new XMLHttpRequest();
        xhr.open('GET', url, false);
        if (binary) xhr.overrideMimeType('text/plain; charset=x-user-defined');
        try {
            xhr.send();
        } catch (e) {
            return null;
        }
        return xhr.status === 200 ? xhr : null;
    }

    function notFound(p, syscall) {
        var error = new Error("ENOENT: no such file or directory, " + syscall + " '" + p + "'");
        error.code = 'ENOENT';
        error.errno = -2;
        error.syscall = syscall;
        error.path = String(p);
        return error;
    }

    function stat(p) {
        var rel = gamePath(p);
        if (rel === null) return null;
        var xhr = request('/__mvplayer/fs/stat/' + encodePath(rel));
        var info = xhr && JSON.parse(xhr.responseText);
        return info && info.exists ? info : null;
    }

    function encodingOf(options) {
        return typeof options === 'string' ? options : options && options.encoding;
    }

    /** Node returns a Buffer without an encoding; a Uint8Array with Buffer's toString does. */
    function buffer(bytes) {
        bytes.toString = function (encoding) {
            return new TextDecoder(encoding && encoding !== 'utf8' ? encoding : 'utf-8').decode(this);
        };
        return bytes;
    }

    function readFileSync(p, options) {
        note('fs.readFileSync');
        var rel = gamePath(p);
        var encoding = encodingOf(options);
        var xhr = rel === null ? null : request(encodePath(rel), !encoding);
        if (!xhr) throw notFound(p, 'open');
        if (encoding) return xhr.responseText;
        var text = xhr.responseText;
        var bytes = new Uint8Array(text.length);
        for (var i = 0; i < text.length; i++) bytes[i] = text.charCodeAt(i) & 0xff;
        return buffer(bytes);
    }

    function statSync(p) {
        note('fs.statSync');
        var info = stat(p);
        if (!info) throw notFound(p, 'stat');
        return {
            size: info.size,
            mtime: new Date(0),
            mtimeMs: 0,
            isFile: function () { return !info.dir; },
            isDirectory: function () { return info.dir; }
        };
    }

    function readdirSync(p, options) {
        note('fs.readdirSync');
        var rel = gamePath(p);
        var xhr = rel === null ? null : request('/__mvplayer/fs/list/' + encodePath(rel));
        if (!xhr) throw notFound(p, 'scandir');
        var entries = JSON.parse(xhr.responseText);
        if (options && options.withFileTypes) {
            return entries.map(function (e) {
                return { name: e.name, isFile: function () { return !e.dir; }, isDirectory: function () { return e.dir; } };
            });
        }
        return entries.map(function (e) { return e.name; });
    }

    /** Wraps a synchronous function as a Node-style callback function. */
    function callback(sync) {
        return function () {
            var args = Array.prototype.slice.call(arguments);
            var done = typeof args[args.length - 1] === 'function' ? args.pop() : null;
            setTimeout(function () {
                var result, error = null;
                try {
                    result = sync.apply(null, args);
                } catch (e) {
                    error = e;
                }
                if (done) done(error, result);
            }, 0);
        };
    }

    function refused(name) {
        return function () {
            note(name + ' (refused: the game folder is read-only here)');
        };
    }

    var fs = {
        existsSync: function (p) {
            note('fs.existsSync');
            return !!stat(p);
        },
        readFileSync: readFileSync,
        statSync: statSync,
        lstatSync: statSync,
        readdirSync: readdirSync,
        realpathSync: function (p) {
            return path.resolve(p);
        },
        accessSync: function (p) {
            if (!stat(p)) throw notFound(p, 'access');
        },
        writeFileSync: refused('fs.writeFileSync'),
        appendFileSync: refused('fs.appendFileSync'),
        mkdirSync: refused('fs.mkdirSync'),
        unlinkSync: refused('fs.unlinkSync'),
        rmdirSync: refused('fs.rmdirSync'),
        renameSync: refused('fs.renameSync'),
        copyFileSync: refused('fs.copyFileSync')
    };
    fs.readFile = callback(readFileSync);
    fs.stat = callback(statSync);
    fs.readdir = callback(readdirSync);
    fs.exists = function (p, done) {
        var exists = fs.existsSync(p);
        setTimeout(function () { if (done) done(exists); }, 0);
    };
    ['writeFile', 'appendFile', 'mkdir', 'unlink', 'rmdir', 'rename', 'copyFile'].forEach(function (name) {
        var refuse = refused('fs.' + name);
        fs[name] = function () {
            refuse();
            var done = arguments[arguments.length - 1];
            if (typeof done === 'function') setTimeout(function () { done(null); }, 0);
        };
    });

    // --- os, NW.js, process ----------------------------------------------------------------

    var os = {
        EOL: '\n',
        platform: function () { return 'android'; },
        type: function () { return 'Linux'; },
        arch: function () { return 'arm64'; },
        homedir: function () { return CWD; },
        tmpdir: function () { return '/tmp'; },
        hostname: function () { return 'android'; },
        cpus: function () { return []; },
        totalmem: function () { return 0; },
        freemem: function () { return 0; }
    };

    var gameWindow = lenient({
        get width() { return window.innerWidth; },
        get height() { return window.innerHeight; },
        x: 0,
        y: 0,
        title: document.title,
        isFullscreen: true,
        zoomLevel: 0,
        close: function () {
            note('nw.Window.close');
            window.close();
        },
        on: noop('nw.Window.on'),
        removeAllListeners: noop('nw.Window.removeAllListeners')
    }, 'nw.Window');

    function Constructor(name) {
        return function () {
            note('new nw.' + name);
            return lenient({}, 'nw.' + name);
        };
    }

    var nw = {
        Window: lenient({ get: function () { return gameWindow; } }, 'nw.Window'),
        App: lenient({
            argv: [],
            fullArgv: [],
            dataPath: CWD,
            manifest: {},
            quit: function () {
                note('nw.App.quit');
                window.close();
            },
            closeAllWindows: function () {
                note('nw.App.closeAllWindows');
                window.close();
            }
        }, 'nw.App'),
        Screen: lenient({ screens: [], Init: noop('nw.Screen.Init') }, 'nw.Screen'),
        Shell: lenient({}, 'nw.Shell'),
        Clipboard: lenient({ get: function () { return lenient({ get: function () { return ''; } }, 'nw.Clipboard'); } }, 'nw.Clipboard'),
        Menu: Constructor('Menu'),
        MenuItem: Constructor('MenuItem'),
        Tray: Constructor('Tray')
    };

    var modules = { fs: fs, path: path, os: os, 'nw.gui': nw };
    var patterns = [];

    function require(name) {
        name = String(name);
        var key = name.replace(/^node:/, '');
        if (Object.prototype.hasOwnProperty.call(modules, key)) {
            note("require('" + key + "')");
            return modules[key];
        }
        for (var i = 0; i < patterns.length; i++) {
            if (patterns[i].pattern.test(name)) {
                note("require('" + name + "')");
                return patterns[i].module;
            }
        }
        MvPlayer.log('w', 'NwJs', "require('" + name + "'): no stand-in, throwing MODULE_NOT_FOUND");
        var error = new Error("Cannot find module '" + name + "'");
        error.code = 'MODULE_NOT_FOUND';
        throw error;
    }

    MvPlayer.node = {
        /** Other patches add modules, e.g. stubs/greenworks.js. */
        register: function (pattern, module) {
            patterns.push({ pattern: pattern, module: module });
        },
        gamePath: gamePath
    };

    if (typeof window.require === 'undefined') window.require = require;
    if (typeof window.nw === 'undefined') window.nw = nw;
    if (typeof window.process === 'undefined') {
        window.process = {
            platform: 'android',
            arch: 'arm64',
            env: {},
            argv: [],
            versions: {},
            execPath: CWD + '/Game',
            mainModule: { filename: WEB + '/index.html' },
            cwd: function () { return CWD; },
            on: noop('process.on'),
            exit: function () {
                note('process.exit');
                window.close();
            },
            nextTick: function (fn) {
                var args = Array.prototype.slice.call(arguments, 1);
                setTimeout(function () { fn.apply(null, args); }, 0);
            }
        };
    }

    // `require` and `process` exist now; MV must still see a browser, not NW.js.
    Utils.isNwjs = function () {
        return false;
    };
})();
