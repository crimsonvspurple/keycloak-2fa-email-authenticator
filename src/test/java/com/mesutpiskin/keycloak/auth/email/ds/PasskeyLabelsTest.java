package com.mesutpiskin.keycloak.auth.email.ds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntSupplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasskeyLabelsTest {

    private static final String CHROME_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String EDGE_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0";
    private static final String SAFARI_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    private static final String SAFARI_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15";
    private static final String FIREFOX_LINUX = "Mozilla/5.0 (X11; Linux x86_64; rv:122.0) Gecko/20100101 Firefox/122.0";
    private static final String CHROME_ANDROID = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String CHROME_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/120.0.6099.119 Mobile/15E148 Safari/604.1";
    private static final String EDGE_ANDROID = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 EdgA/120.0.0.0";
    private static final String CHROME_OS = "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String SAMSUNG = "Mozilla/5.0 (Linux; Android 14; SAMSUNG SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/23.0 Chrome/115.0.0.0 Mobile Safari/537.36";
    private static final String OPERA_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 OPR/106.0.0.0";

    @Test
    void namesTheBrowserAndDevice() {
        assertEquals("Chrome on Windows", PasskeyLabels.describeUserAgent(CHROME_WINDOWS));
        assertEquals("Edge on macOS", PasskeyLabels.describeUserAgent(EDGE_MAC));
        assertEquals("Safari on iPhone", PasskeyLabels.describeUserAgent(SAFARI_IPHONE));
        assertEquals("Safari on macOS", PasskeyLabels.describeUserAgent(SAFARI_MAC));
        assertEquals("Firefox on Linux", PasskeyLabels.describeUserAgent(FIREFOX_LINUX));
        assertEquals("Chrome on Android", PasskeyLabels.describeUserAgent(CHROME_ANDROID));
        assertEquals("Chrome on iPhone", PasskeyLabels.describeUserAgent(CHROME_IPHONE));
        assertEquals("Edge on Android", PasskeyLabels.describeUserAgent(EDGE_ANDROID));
        assertEquals("Chrome on ChromeOS", PasskeyLabels.describeUserAgent(CHROME_OS));
        assertEquals("Samsung Internet on Android", PasskeyLabels.describeUserAgent(SAMSUNG));
        assertEquals("Opera on Windows", PasskeyLabels.describeUserAgent(OPERA_WINDOWS));
        assertEquals("Browser on this device", PasskeyLabels.describeUserAgent(null));
        assertEquals("Browser on this device", PasskeyLabels.describeUserAgent("curl/8.0"));
    }

    @Test
    void keepsAUniqueSubmittedName() {
        String label = PasskeyLabels.uniqueLabel("Chrome on Windows 4821", CHROME_WINDOWS, List.of(), () -> 1111);
        assertEquals("Chrome on Windows 4821", label);
    }

    @Test
    void fillsABlankOrDefaultNameFromTheUserAgent() {
        IntSupplier suffixes = () -> 4821;
        assertEquals("Safari on iPhone 4821", PasskeyLabels.uniqueLabel(null, SAFARI_IPHONE, List.of(), suffixes));
        assertEquals("Safari on iPhone 4821", PasskeyLabels.uniqueLabel("  ", SAFARI_IPHONE, List.of(), suffixes));
        assertEquals("Edge on macOS 4821", PasskeyLabels.uniqueLabel("Passkey (Default Label)", EDGE_MAC, List.of(), suffixes));
        assertEquals("Chrome on Windows 4821", PasskeyLabels.uniqueLabel("Passkey", CHROME_WINDOWS, List.of(), suffixes));
    }

    @Test
    void replacesTheSuffixWhenTheNameAlreadyExists() {
        int[] next = {4821};
        String label = PasskeyLabels.uniqueLabel(
                "Chrome on Windows 4821",
                CHROME_WINDOWS,
                List.of("chrome on windows 4821"),
                () -> next[0]++);
        assertEquals("Chrome on Windows 4822", label);
    }

    @Test
    void treatsADifferentlyCasedNameAsTheSameDevice() {
        String label = PasskeyLabels.uniqueLabel(
                "Safari on iPhone 1000",
                SAFARI_IPHONE,
                List.of("  SAFARI ON IPHONE 1000  "),
                () -> 1001);
        assertEquals("Safari on iPhone 1001", label);
    }

    @Test
    void findsAFreeSuffixWhenRandomGuessesCollide() {
        Set<String> taken = new HashSet<>();
        for (int number = 0; number < 10_000; number++) {
            taken.add(String.format(Locale.ROOT, "Chrome on Windows %04d", number));
        }
        String label = PasskeyLabels.uniqueLabel("Chrome on Windows 4821", CHROME_WINDOWS, taken, () -> 4821);
        assertFalse(taken.stream().anyMatch(label::equalsIgnoreCase));
        assertTrue(label.startsWith("Chrome on Windows "));
        assertTrue(label.length() <= PasskeyLabels.MAX_LENGTH);
        assertTrue(label.length() > "Chrome on Windows 4821".length());
    }

    @Test
    void staysWithinTheCredentialLabelColumn() {
        String submitted = "S".repeat(400);
        List<String> existing = List.of("S".repeat(PasskeyLabels.MAX_LENGTH));
        String label = PasskeyLabels.uniqueLabel(submitted, null, existing, () -> 7);
        assertTrue(label.length() <= PasskeyLabels.MAX_LENGTH);
        assertFalse(label.equalsIgnoreCase(existing.get(0)));
        assertTrue(label.endsWith(" 0007"));
    }

    @Test
    void everyGeneratedNameIsUniqueAndShortEnough() {
        List<String> existing = new ArrayList<>();
        for (int number = 0; number < 20; number++) {
            int suffix = number;
            String label = PasskeyLabels.uniqueLabel("Chrome on Windows 0000", CHROME_WINDOWS, existing, () -> suffix);
            assertTrue(label.length() <= PasskeyLabels.MAX_LENGTH);
            assertFalse(existing.stream().anyMatch(label::equalsIgnoreCase));
            existing.add(label);
        }
    }
}
