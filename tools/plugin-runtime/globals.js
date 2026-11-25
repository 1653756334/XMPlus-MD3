// Globals MusicFree plugins find in React Native and QuickJS lacks: timers, base64, text coding, URL, fetch.
// Imported first by entry.js so packages that capture them while loading (webdav captures fetch) see them.
// Network and timers go through two host functions installed by core:plugin before this file runs:
//   __mfHttp(requestJson) -> Promise<responseJson>, __mfDelay(ms) -> Promise.
import { Buffer } from "buffer";
import { URL, URLSearchParams } from "whatwg-url-without-unicode";

const g = globalThis;

// Timers
let nextTimer = 1;
const liveTimers = new Set();
function later(fn, ms, args, repeat) {
    const id = nextTimer++;
    liveTimers.add(id);
    const wait = Math.max(0, Number(ms) || 0);
    const tick = () => g.__mfDelay(wait).then(() => {
        if (!liveTimers.has(id)) return;
        if (!repeat) liveTimers.delete(id);
        if (typeof fn === "function") fn(...args);
        if (repeat && liveTimers.has(id)) tick();
    });
    tick();
    return id;
}
if (typeof g.setTimeout !== "function") {
    g.setTimeout = (fn, ms, ...args) => later(fn, ms, args, false);
    g.setInterval = (fn, ms, ...args) => later(fn, ms, args, true);
    g.clearTimeout = (id) => { liveTimers.delete(id); };
    g.clearInterval = g.clearTimeout;
}
if (typeof g.setImmediate !== "function") {
    g.setImmediate = (fn, ...args) => g.setTimeout(fn, 0, ...args);
    g.clearImmediate = g.clearTimeout;
}
if (typeof g.queueMicrotask !== "function") {
    g.queueMicrotask = (fn) => { Promise.resolve().then(fn); };
}

// Base64 and text
if (typeof g.btoa !== "function") {
    g.btoa = (s) => Buffer.from(String(s), "latin1").toString("base64");
    g.atob = (s) => Buffer.from(String(s), "base64").toString("latin1");
}
if (typeof g.TextEncoder !== "function") {
    g.TextEncoder = class TextEncoder {
        get encoding() { return "utf-8"; }
        encode(s = "") { return new Uint8Array(Buffer.from(String(s), "utf8")); }
    };
    g.TextDecoder = class TextDecoder {
        constructor(label = "utf-8") { this.encoding = String(label).toLowerCase(); }
        decode(b) {
            if (b == null) return "";
            const bytes = b instanceof ArrayBuffer ? Buffer.from(b) : Buffer.from(b.buffer, b.byteOffset, b.byteLength);
            return bytes.toString(this.encoding === "utf-16le" ? "utf16le" : "utf8");
        }
    };
}

if (typeof g.URL !== "function") g.URL = URL;
if (typeof g.URLSearchParams !== "function") g.URLSearchParams = URLSearchParams;

// fetch, backed by the same OkHttp bridge as axios
class Headers {
    constructor(init) {
        this._map = new Map();
        if (init instanceof Headers) init.forEach((v, k) => this.append(k, v));
        else if (Array.isArray(init)) init.forEach(([k, v]) => this.append(k, v));
        else if (init) Object.keys(init).forEach((k) => this.append(k, init[k]));
    }
    append(k, v) {
        const key = String(k).toLowerCase();
        const old = this._map.get(key);
        this._map.set(key, old == null ? String(v) : old + ", " + v);
    }
    set(k, v) { this._map.set(String(k).toLowerCase(), String(v)); }
    get(k) { const v = this._map.get(String(k).toLowerCase()); return v == null ? null : v; }
    has(k) { return this._map.has(String(k).toLowerCase()); }
    delete(k) { this._map.delete(String(k).toLowerCase()); }
    forEach(fn, self) { this._map.forEach((v, k) => fn.call(self, v, k, this)); }
    entries() { return this._map.entries(); }
    keys() { return this._map.keys(); }
    values() { return this._map.values(); }
    [Symbol.iterator]() { return this._map.entries(); }
}

function bodyOf(data) {
    if (data == null) return {};
    if (typeof data === "string") return { body: data };
    if (data instanceof URLSearchParams) return { body: data.toString() };
    if (data instanceof ArrayBuffer) return { bodyBase64: Buffer.from(data).toString("base64") };
    if (ArrayBuffer.isView(data)) return { bodyBase64: Buffer.from(data.buffer, data.byteOffset, data.byteLength).toString("base64") };
    return { body: String(data) };
}

class Response {
    constructor(bytes, init = {}) {
        this._bytes = bytes == null ? Buffer.alloc(0) : Buffer.isBuffer(bytes) ? bytes : Buffer.from(String(bytes), "utf8");
        this.status = init.status ?? 200;
        this.statusText = init.statusText ?? "";
        this.headers = init.headers instanceof Headers ? init.headers : new Headers(init.headers);
        this.url = init.url ?? "";
        this.ok = this.status >= 200 && this.status < 300;
        this.redirected = false;
        this.bodyUsed = false;
    }
    async arrayBuffer() { this.bodyUsed = true; const b = this._bytes; return b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength); }
    async text() { this.bodyUsed = true; return this._bytes.toString("utf8"); }
    async json() { return JSON.parse(await this.text()); }
    clone() { return new Response(Buffer.from(this._bytes), this); }
}

class Request {
    constructor(input, init = {}) {
        this.url = input instanceof Request ? input.url : String(input);
        this.method = (init.method || (input instanceof Request ? input.method : "GET")).toUpperCase();
        this.headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));
        this.body = init.body ?? (input instanceof Request ? input.body : undefined);
    }
}

async function fetch(input, init = {}) {
    const req = input instanceof Request ? input : new Request(input, init);
    const headers = {};
    new Headers(init.headers || req.headers).forEach((v, k) => { headers[k] = v; });
    const res = JSON.parse(await g.__mfHttp(JSON.stringify({
        method: (init.method || req.method || "GET").toUpperCase(),
        url: req.url,
        headers,
        ...bodyOf(init.body ?? req.body),
        timeout: 0,
        binary: true,
    })));
    if (res.error) throw new TypeError("Network request failed");
    const h = new Headers();
    res.headers.forEach(([k, v]) => h.append(k, v));
    return new Response(Buffer.from(res.bodyBase64 || "", "base64"), { status: res.status, statusText: res.statusText, headers: h, url: res.url });
}

g.Headers = Headers;
g.Request = Request;
g.Response = Response;
g.fetch = fetch;

export { bodyOf };
