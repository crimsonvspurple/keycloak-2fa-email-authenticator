// Names a passkey from the browser and device. No network and no libraries.
// navigator.userAgentData is preferred. A short userAgent parse covers Safari
// and other browsers that do not expose client hints.
// Keep the names in sync with PasskeyLabels.describeUserAgent.

export const MAX_LABEL_LENGTH = 255;

const BROWSERS = [
    ["microsoft edge", "Edge"],
    ["edge", "Edge"],
    ["opera", "Opera"],
    ["brave", "Brave"],
    ["samsung internet", "Samsung Internet"],
    ["google chrome", "Chrome"],
    ["chrome", "Chrome"],
    ["chromium", "Chromium"],
    ["firefox", "Firefox"],
    ["safari", "Safari"]
];

const PLATFORMS = [
    [/android/i, "Android"],
    [/cros|chrome\s*os/i, "ChromeOS"],
    [/windows/i, "Windows"],
    [/mac/i, "macOS"],
    [/ios|iphone|ipad/i, "iOS"],
    [/linux/i, "Linux"]
];

export function passkeyLabel(nav, randomInt) {
    return passkeyLabelFromDescription(describeClient(nav), randomInt);
}

export function passkeyLabelFromDescription(description, randomInt) {
    return fit(description, fourDigits(randomInt));
}

export function describeClient(nav) {
    const source = nav || {};
    const browser = browserName(source) || "Browser";
    const device = deviceName(source) || "this device";
    return browser + " on " + device;
}

function browserName(nav) {
    const data = nav.userAgentData;
    const fromHints = data ? browserFromBrands(data.brands) : "";
    if (fromHints) return fromHints;
    return browserFromUa(String(nav.userAgent || ""));
}

function browserFromBrands(brands) {
    if (!Array.isArray(brands)) return "";
    const names = brands
        .map(function (entry) {
            if (!entry) return "";
            return String(entry.brand || entry).trim().toLowerCase();
        })
        .filter(function (name) {
            return name && !isGrease(name);
        });
    for (const known of BROWSERS) {
        if (names.some(function (name) { return name.includes(known[0]); })) {
            return known[1];
        }
    }
    return "";
}

function isGrease(brand) {
    return brand.replace(/[^a-z]/gi, "").toLowerCase() === "notabrand";
}

function browserFromUa(ua) {
    if (/Edg(?:e|A|iOS)?\//.test(ua)) return "Edge";
    if (/OPR\/|Opera\//.test(ua)) return "Opera";
    if (/SamsungBrowser\//.test(ua)) return "Samsung Internet";
    if (/CriOS\//.test(ua)) return "Chrome";
    if (/FxiOS\/|Firefox\//.test(ua)) return "Firefox";
    if (/Chrome\//.test(ua)) return "Chrome";
    if (/Chromium\//.test(ua)) return "Chromium";
    if (/Safari\//.test(ua)) return "Safari";
    return "";
}

function deviceName(nav) {
    const ua = String(nav.userAgent || "");
    if (/iPhone/i.test(ua)) return "iPhone";
    if (/iPad/i.test(ua)) return "iPad";
    if (/iPod/i.test(ua)) return "iPod";
    if (nav.platform === "MacIntel" && Number(nav.maxTouchPoints) > 1) return "iPad";

    const data = nav.userAgentData;
    if (data && data.platform) {
        const hinted = platformName(data.platform);
        if (hinted) return hinted;
    }
    if (/Android/i.test(ua)) return "Android";
    if (/Windows/i.test(ua)) return "Windows";
    if (/CrOS/i.test(ua)) return "ChromeOS";
    if (/Mac OS X|Macintosh/i.test(ua)) return "macOS";
    if (/Linux/i.test(ua)) return "Linux";
    return "";
}

function platformName(platform) {
    const value = String(platform || "");
    for (const known of PLATFORMS) {
        if (known[0].test(value)) return known[1];
    }
    return "";
}

function fourDigits(randomInt) {
    let value;
    if (typeof randomInt === "function") {
        value = Number(randomInt());
    } else if (globalThis.crypto && typeof crypto.getRandomValues === "function") {
        const bytes = new Uint16Array(1);
        crypto.getRandomValues(bytes);
        value = bytes[0];
    } else {
        value = Math.floor(Math.random() * 10000);
    }
    if (!Number.isFinite(value)) value = 0;
    value = Math.abs(Math.trunc(value)) % 10000;
    return String(value).padStart(4, "0");
}

function fit(description, suffix) {
    const tail = " " + suffix;
    let stem = String(description || "Passkey").replace(/\s+/g, " ").trim();
    const maxStem = MAX_LABEL_LENGTH - tail.length;
    if (stem.length > maxStem) stem = stem.slice(0, maxStem).trim();
    if (!stem) stem = "Passkey";
    if (stem.length > maxStem) stem = stem.slice(0, maxStem);
    return stem + tail;
}
