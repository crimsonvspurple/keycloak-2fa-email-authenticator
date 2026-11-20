package com.mesutpiskin.keycloak.auth.email.ds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DsBrokerLinkTest {

    @Test
    void accountConsoleLinkStaysOnTheSignedInUser() {
        assertTrue(DsBrokerLink.linkSignedInAccount("signed-in"));
        assertFalse(DsBrokerLink.linkVerifiedEmail("signed-in", true));
        assertFalse(DsBrokerLink.linkVerifiedEmail("signed-in", false));
    }

    @Test
    void firstLoginLinksOnlyAVerifiedEmail() {
        assertFalse(DsBrokerLink.linkSignedInAccount(null));
        assertFalse(DsBrokerLink.linkSignedInAccount("  "));
        assertTrue(DsBrokerLink.linkVerifiedEmail(null, true));
        assertFalse(DsBrokerLink.linkVerifiedEmail(null, false));
    }
}
