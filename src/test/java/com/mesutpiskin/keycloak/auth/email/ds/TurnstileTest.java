package com.mesutpiskin.keycloak.auth.email.ds;

import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnstileTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void acceptsOnlyTheMatchingActionAndHostname() throws Exception {
        var body = JSON.readTree("""
                {"success":true,"action":"login","hostname":"auth.dobbersports.com"}
                """);
        assertTrue(Turnstile.accepted(body, Turnstile.LOGIN, Set.of("auth.dobbersports.com")));
        assertFalse(Turnstile.accepted(body, Turnstile.REGISTER, Set.of("auth.dobbersports.com")));
        assertFalse(Turnstile.accepted(body, Turnstile.LOGIN, Set.of("localhost")));
        assertFalse(Turnstile.accepted(JSON.readTree("{\"success\":false}"), Turnstile.LOGIN, Set.of("auth.dobbersports.com")));
    }

    @Test
    void matchesTheBypassSecretExactly() {
        assertTrue(Turnstile.matches("secret-value", "secret-value"));
        assertTrue(Turnstile.matches("secret-value", " secret-value "));
        assertFalse(Turnstile.matches("secret-value", "secret-valuE"));
        assertFalse(Turnstile.matches("secret-value", "secret-value-extra"));
        assertFalse(Turnstile.matches("secret-value", ""));
        assertFalse(Turnstile.matches("secret-value", null));
        assertFalse(Turnstile.matches("", "secret-value"));
        assertFalse(Turnstile.matches("   ", "secret-value"));
        assertFalse(Turnstile.matches("secret-value", "x".repeat(513)));
    }

    @Test
    void blankServerSecretDoesNotHonorASessionNote() {
        assertFalse(Turnstile.bypasses("", true, "secret-value"));
        assertFalse(Turnstile.bypasses(null, true, "secret-value"));
        assertFalse(Turnstile.bypasses("   ", true, "secret-value"));
        assertTrue(Turnstile.bypasses("secret-value", true, null));
        assertTrue(Turnstile.bypasses("secret-value", false, "secret-value"));
        assertFalse(Turnstile.bypasses("secret-value", false, "wrong"));
        assertFalse(Turnstile.bypasses("secret-value", false, null));
    }
}
