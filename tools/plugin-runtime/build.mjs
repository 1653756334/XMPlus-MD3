// Builds entry.js into the asset core:plugin loads into every plugin context. Run `npm ci && npm run build` after
// changing a package version; the output is committed so the app build never needs Node.
import { build } from "esbuild";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));

await build({
    entryPoints: [path.join(here, "entry.js")],
    outfile: path.join(here, "../../core/plugin/src/main/assets/musicfree/packages.js"),
    bundle: true,
    format: "iife",
    platform: "browser",
    target: "es2020",
    minify: true,
    legalComments: "eof",
    define: {
        "process.env.NODE_ENV": '"production"',
        global: "globalThis",
    },
    logLevel: "info",
});
