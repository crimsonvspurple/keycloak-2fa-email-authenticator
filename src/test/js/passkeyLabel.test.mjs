import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
    describeClient,
    MAX_LABEL_LENGTH,
    passkeyLabel,
    passkeyLabelFromDescription
} from "../../../themes/ds/login/resources/js/passkeyLabel.js";

const root = fileURLToPath(new URL("../../..", import.meta.url));

const CHROME_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
const EDGE_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0";
const SAFARI_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
const SAFARI_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15";
const FIREFOX_LINUX = "Mozilla/5.0 (X11; Linux x86_64; rv:122.0) Gecko/20100101 Firefox/122.0";
const CHROME_ANDROID = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
const CHROME_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/120.0.6099.119 Mobile/15E148 Safari/604.1";
const EDGE_ANDROID = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 EdgA/120.0.0.0";
const CHROME_OS = "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
const SAMSUNG = "Mozilla/5.0 (Linux; Android 14; SAMSUNG SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/23.0 Chrome/115.0.0.0 Mobile Safari/537.36";
const OPERA_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 OPR/106.0.0.0";

test("names the browser and device from a user agent", () => {
    assert.equal(describeClient({ userAgent: CHROME_WINDOWS }), "Chrome on Windows");
    assert.equal(describeClient({ userAgent: EDGE_MAC }), "Edge on macOS");
    assert.equal(describeClient({ userAgent: SAFARI_IPHONE }), "Safari on iPhone");
    assert.equal(describeClient({ userAgent: SAFARI_MAC }), "Safari on macOS");
    assert.equal(describeClient({ userAgent: FIREFOX_LINUX }), "Firefox on Linux");
    assert.equal(describeClient({ userAgent: CHROME_ANDROID }), "Chrome on Android");
    assert.equal(describeClient({ userAgent: CHROME_IPHONE }), "Chrome on iPhone");
    assert.equal(describeClient({ userAgent: EDGE_ANDROID }), "Edge on Android");
    assert.equal(describeClient({ userAgent: CHROME_OS }), "Chrome on ChromeOS");
    assert.equal(describeClient({ userAgent: SAMSUNG }), "Samsung Internet on Android");
    assert.equal(describeClient({ userAgent: OPERA_WINDOWS }), "Opera on Windows");
    assert.equal(describeClient({}), "Browser on this device");
    assert.equal(describeClient({ userAgent: "curl/8.0" }), "Browser on this device");
});

test("prefers userAgentData over a misleading user agent", () => {
    const nav = {
        userAgent: FIREFOX_LINUX,
        userAgentData: {
            brands: [
                { brand: "Not_A Brand", version: "24" },
                { brand: "Chromium", version: "120" },
                { brand: "Google Chrome", version: "120" }
            ],
            mobile: false,
            platform: "Windows"
        }
    };
    assert.equal(describeClient(nav), "Chrome on Windows");
});

test("names Edge on macOS from client hints", () => {
    const nav = {
        userAgent: CHROME_WINDOWS,
        userAgentData: {
            brands: [
                { brand: "Not;A=Brand", version: "8" },
                { brand: "Chromium", version: "120" },
                { brand: "Microsoft Edge", version: "120" }
            ],
            mobile: false,
            platform: "macOS"
        }
    };
    assert.equal(describeClient(nav), "Edge on macOS");
    assert.equal(passkeyLabel(nav, () => 4821), "Edge on macOS 4821");
});

test("calls an iPad in desktop mode an iPad", () => {
    const nav = {
        userAgent: SAFARI_MAC,
        platform: "MacIntel",
        maxTouchPoints: 5
    };
    assert.equal(describeClient(nav), "Safari on iPad");
});

test("an iPhone user agent wins over a desktop platform hint", () => {
    const nav = {
        userAgent: CHROME_IPHONE,
        userAgentData: {
            brands: [{ brand: "Google Chrome", version: "120" }],
            mobile: true,
            platform: "macOS"
        }
    };
    assert.equal(describeClient(nav), "Chrome on iPhone");
});

test("adds a four digit suffix and stays within Keycloak's label length", () => {
    assert.equal(passkeyLabel({ userAgent: CHROME_WINDOWS }, () => 4821), "Chrome on Windows 4821");
    assert.equal(passkeyLabel({ userAgent: SAFARI_IPHONE }, () => 7), "Safari on iPhone 0007");
    const fitted = passkeyLabelFromDescription("A".repeat(400), () => 4821);
    assert.equal(fitted.length, MAX_LABEL_LENGTH);
    assert.ok(fitted.endsWith(" 4821"));
    assert.ok(fitted.length <= 255);
});

test("the registration page does not ask for a label", () => {
    const page = readFileSync(`${root}/themes/ds/login/webauthn-register.ftl`, "utf8");
    const script = readFileSync(`${root}/themes/ds/login/resources/js/webauthnRegister.js`, "utf8");
    assert.doesNotMatch(page, /window\.prompt/);
    assert.doesNotMatch(script, /window\.prompt/);
    assert.match(page, /passkeyLabel\(navigator\)/);
    assert.match(script, /authenticatorLabel/);
    assert.match(page, /webauthnRegister\.js\?v=/);
});
