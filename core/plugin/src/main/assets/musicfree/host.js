// Runs after packages.js in every plugin context. Mirrors MusicFree's mountPlugin (src/core/pluginManager/plugin.ts):
// the same arguments, `require`, `env` and `process`, and the same checks after the code has run.
// Host functions installed by core:plugin: __mfLog(level, text), __mfUserVariables() -> json, __mfSource() -> code.
(function () {
    "use strict";
    const g = globalThis;
    const mf = g.__mf;
    const appVersion = g.__mfAppVersion;

    const noop = function () {};
    const deprecatedCookieManager = { get: noop, set: noop, flush: noop };

    function _require(name) {
        const pkg = name === "@react-native-cookies/cookies" ? deprecatedCookieManager : mf.packages[name];
        if (pkg == null) return undefined;
        pkg.default = pkg;
        return pkg;
    }

    function text(args) {
        return args.map(function (a) {
            if (typeof a === "string") return a;
            if (a instanceof Error) return a.stack || String(a);
            try { return JSON.stringify(a); } catch (e) { return String(a); }
        }).join(" ");
    }
    const _console = {};
    ["log", "info", "warn", "error"].forEach(function (level) {
        _console[level] = function () { g.__mfLog(level, text(Array.prototype.slice.call(arguments))); };
    });

    const env = {
        getUserVariables: function () { return JSON.parse(g.__mfUserVariables()); },
        get userVariables() { return this.getUserVariables() || {}; },
        appVersion: appVersion,
        os: "android",
        lang: "zh-CN",
    };
    const _process = { platform: "android", version: appVersion, env: env };

    let instance = null;

    g.__mfLoad = function () {
        const code = g.__mfSource();
        const module = { exports: {} };
        const result = { error: null };
        try {
            // eslint-disable-next-line no-new-func
            Function("'use strict';\nreturn function(require, __musicfree_require, module, exports, console, env, URL, process) {\n" +
                code + "\n}")()(_require, _require, module, module.exports, _console, env, g.URL, _process);
            instance = module.exports.default ? module.exports.default : module.exports;
            if (Array.isArray(instance.userVariables)) {
                instance.userVariables = instance.userVariables.filter(function (it) { return it && it.key; });
            }
            if (instance.appVersion) {
                let ok = false;
                try { ok = mf.satisfies(appVersion, instance.appVersion); } catch (e) { ok = false; }
                if (!ok) result.error = "version_not_match";
            }
        } catch (e) {
            result.error = "cannot_parse";
            result.message = String(e && e.message || e);
            instance = null;
        }
        const it = instance || {};
        result.platform = typeof it.platform === "string" ? it.platform : null;
        if (!result.platform && !result.error) result.error = "cannot_parse";
        result.methods = Object.keys(it).filter(function (k) { return typeof it[k] === "function"; });
        ["version", "appVersion", "author", "description", "srcUrl", "primaryKey", "cacheControl", "defaultSearchType",
            "supportedSearchType", "userVariables", "hints"].forEach(function (k) {
            const v = it[k];
            if (v !== undefined && typeof v !== "function") result[k] = v;
        });
        return JSON.stringify(result);
    };

    // One plugin method call. Arguments and result cross as JSON, so items keep every field the plugin gave them.
    g.__mfCall = async function (method, argsJson) {
        const target = instance && instance[method];
        if (typeof target !== "function") return JSON.stringify({ missing: true });
        const value = await target.apply(instance, JSON.parse(argsJson));
        return JSON.stringify({ value: value === undefined ? null : value });
    };
})();
