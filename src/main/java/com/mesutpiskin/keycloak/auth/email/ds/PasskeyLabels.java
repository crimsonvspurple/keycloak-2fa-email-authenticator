package com.mesutpiskin.keycloak.auth.email.ds;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.Locale;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;

/**
 * Passkey names. The browser sends "Chrome on Windows 4821". This makes sure
 * the stored name is unique and fits the credential user-label column.
 * Browser and device words match themes/ds/login/resources/js/passkeyLabel.js.
 */
final class PasskeyLabels {

    static final int MAX_LENGTH = 255;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern SUFFIX = Pattern.compile(" \\d{4}$");

    private PasskeyLabels() {
    }

    static String uniqueLabel(String submitted, String userAgent, Collection<String> existing) {
        return uniqueLabel(submitted, userAgent, existing, PasskeyLabels::randomSuffix);
    }

    static String uniqueLabel(String submitted, String userAgent, Collection<String> existing, IntSupplier suffixes) {
        String label;
        if (generatedName(submitted)) {
            label = withSuffix(describeUserAgent(userAgent), suffix(suffixes));
        } else {
            label = submitted.trim();
            if (label.length() > MAX_LENGTH) {
                label = label.substring(0, MAX_LENGTH).trim();
            }
        }
        if (!taken(label, existing)) {
            return label;
        }
        String stem = stem(label);
        for (int attempt = 0; attempt < 32; attempt++) {
            String candidate = withSuffix(stem, suffix(suffixes));
            if (!taken(candidate, existing)) {
                return candidate;
            }
        }
        for (int number = 0; number < 10_000; number++) {
            String candidate = withSuffix(stem, String.format(Locale.ROOT, "%04d", number));
            if (!taken(candidate, existing)) {
                return candidate;
            }
        }
        for (int extra = 0; extra < 100; extra++) {
            String candidate = withSuffix(stem, suffix(suffixes) + String.format(Locale.ROOT, "%02d", extra));
            if (!taken(candidate, existing)) {
                return candidate;
            }
        }
        return withSuffix(stem, suffix(suffixes) + Long.toUnsignedString(RANDOM.nextLong()));
    }

    /**
     * Browser and device from a User-Agent header. Client hints are only
     * available in the browser, so this is the fallback when the form has no name.
     */
    static String describeUserAgent(String userAgent) {
        String ua = userAgent == null ? "" : userAgent;
        String browser = browser(ua);
        String device = device(ua);
        if (browser.isEmpty()) {
            browser = "Browser";
        }
        if (device.isEmpty()) {
            device = "this device";
        }
        return browser + " on " + device;
    }

    private static boolean generatedName(String submitted) {
        if (submitted == null || submitted.isBlank()) {
            return true;
        }
        String trimmed = submitted.trim();
        return trimmed.equalsIgnoreCase("Passkey")
                || trimmed.equalsIgnoreCase("Passkey (Default Label)")
                || trimmed.equalsIgnoreCase("WebAuthn Authenticator (Default Label)");
    }

    private static String stem(String label) {
        String stripped = SUFFIX.matcher(label.trim()).replaceFirst("").trim();
        return stripped.isEmpty() ? "Passkey" : stripped;
    }

    private static String withSuffix(String description, String suffix) {
        String tail = " " + suffix;
        if (tail.length() >= MAX_LENGTH) {
            return tail.substring(tail.length() - MAX_LENGTH);
        }
        String stem = description == null ? "" : description.trim().replaceAll("\\s+", " ");
        int maxStem = MAX_LENGTH - tail.length();
        if (stem.length() > maxStem) {
            stem = stem.substring(0, maxStem).trim();
        }
        if (stem.isEmpty()) {
            stem = "Passkey";
        }
        if (stem.length() > maxStem) {
            stem = stem.substring(0, maxStem);
        }
        return stem + tail;
    }

    private static boolean taken(String label, Collection<String> existing) {
        if (label == null || existing == null) {
            return false;
        }
        String needle = label.trim();
        for (String current : existing) {
            if (current != null && current.trim().equalsIgnoreCase(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String suffix(IntSupplier suffixes) {
        int value = suffixes == null ? randomSuffix() : suffixes.getAsInt();
        return String.format(Locale.ROOT, "%04d", Math.floorMod(value, 10_000));
    }

    private static int randomSuffix() {
        return RANDOM.nextInt(10_000);
    }

    private static String browser(String ua) {
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/") || ua.contains("Edge/")) {
            return "Edge";
        }
        if (ua.contains("OPR/") || ua.contains("Opera/")) {
            return "Opera";
        }
        if (ua.contains("SamsungBrowser/")) {
            return "Samsung Internet";
        }
        if (ua.contains("CriOS/")) {
            return "Chrome";
        }
        if (ua.contains("FxiOS/") || ua.contains("Firefox/")) {
            return "Firefox";
        }
        if (ua.contains("Chrome/")) {
            return "Chrome";
        }
        if (ua.contains("Chromium/")) {
            return "Chromium";
        }
        if (ua.contains("Safari/")) {
            return "Safari";
        }
        return "";
    }

    private static String device(String ua) {
        if (ua.contains("iPhone")) {
            return "iPhone";
        }
        if (ua.contains("iPad")) {
            return "iPad";
        }
        if (ua.contains("iPod")) {
            return "iPod";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("CrOS")) {
            return "ChromeOS";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return "";
    }
}
