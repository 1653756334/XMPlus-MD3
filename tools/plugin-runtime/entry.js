// The packages MusicFree 0.6.2 hands to plugins through `require`, with axios set up as MusicFree sets it up.
// The host reads them from `globalThis.__mf`. globals.js comes first so packages see fetch, timers and URL.
import { bodyOf } from "./globals.js";
import axios from "axios";
import bigInt from "big-integer";
import * as cheerio from "cheerio";
import { satisfies } from "compare-versions";
import CryptoJs from "crypto-js";
import dayjs from "dayjs";
import he from "he";
import qs from "qs";
import * as webdav from "webdav";
import { Buffer } from "buffer";

const g = globalThis;

// Requests go through OkHttp: no CORS, every header passes as given.
function okHttpAdapter(config) {
    return new Promise((resolve, reject) => {
        const headers = {};
        const raw = config.headers && typeof config.headers.toJSON === "function" ? config.headers.toJSON() : config.headers || {};
        Object.keys(raw).forEach((k) => { if (raw[k] != null && raw[k] !== false) headers[k] = String(raw[k]); });
        const request = {
            method: (config.method || "get").toUpperCase(),
            url: axios.getUri(config),
            headers,
            ...bodyOf(config.data),
            timeout: config.timeout || 0,
            binary: config.responseType === "arraybuffer" || config.responseType === "blob",
            maxRedirects: config.maxRedirects ?? 5,
        };
        g.__mfHttp(JSON.stringify(request)).then((text) => {
            const res = JSON.parse(text);
            if (res.error) {
                const code = res.error === "timeout" ? axios.AxiosError.ECONNABORTED : axios.AxiosError.ERR_NETWORK;
                const message = res.error === "timeout" ? `timeout of ${config.timeout}ms exceeded` : "Network Error";
                reject(new axios.AxiosError(message, code, config, request));
                return;
            }
            // React Native reports repeated headers as one value joined by ", "; MusicFree's set-cookie fix relies on it.
            const joined = {};
            res.headers.forEach(([k, v]) => {
                const key = k.toLowerCase();
                joined[key] = joined[key] == null ? v : joined[key] + ", " + v;
            });
            if (joined["set-cookie"] != null) joined["set-cookie"] = [joined["set-cookie"]];
            const data = request.binary
                ? (() => { const b = Buffer.from(res.bodyBase64 || "", "base64"); return b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength); })()
                : res.body;
            const response = {
                data,
                status: res.status,
                statusText: res.statusText || "",
                headers: new axios.AxiosHeaders(joined),
                config,
                request: { responseURL: res.url },
            };
            const validate = config.validateStatus;
            if (!validate || validate(response.status)) resolve(response);
            else reject(new axios.AxiosError(
                "Request failed with status code " + response.status,
                response.status >= 500 ? axios.AxiosError.ERR_BAD_RESPONSE : axios.AxiosError.ERR_BAD_REQUEST,
                config, response.request, response,
            ));
        }, (e) => reject(new axios.AxiosError(String(e && e.message || e), axios.AxiosError.ERR_NETWORK, config, request)));
    });
}

axios.defaults.adapter = okHttpAdapter;
// As MusicFree: 2 s default timeout, and a single set-cookie value split on commas with the original kept.
axios.defaults.timeout = 2000;
axios.interceptors.response.use((response) => {
    const setCookie = response.headers["set-cookie"];
    if (setCookie && setCookie.length === 1) {
        response.headers["set-cookie"] = setCookie[0].split(",");
        response.headers["x-set-cookie"] = setCookie;
    }
    return response;
});

g.__mf = {
    packages: {
        cheerio,
        "crypto-js": CryptoJs,
        axios,
        dayjs,
        "big-integer": bigInt,
        qs,
        he,
        webdav,
    },
    satisfies,
    sha256: (text) => CryptoJs.SHA256(text).toString(),
};
