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
}
