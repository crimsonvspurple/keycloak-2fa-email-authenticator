package com.mesutpiskin.keycloak.auth.email.ds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DsAuthCodesTest {

    @Test
    void resendWaitStartsAtTwoMinutes() {
        long sent = 1_000_000L;
        assertEquals(120, DsAuthCodes.waitSeconds(sent, sent));
        assertEquals(120, DsAuthCodes.waitSeconds(sent, sent + 1));
        assertEquals(61, DsAuthCodes.waitSeconds(sent, sent + 59_000L));
        assertEquals(1, DsAuthCodes.waitSeconds(sent, sent + 119_001L));
        assertEquals(0, DsAuthCodes.waitSeconds(sent, sent + 120_000L));
    }
}
