package com.mesutpiskin.keycloak.auth.email.ds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void unverifiedMatchCanBeConfirmedWhenTheEmailsAreTheSame() {
        assertTrue(DsBrokerLink.canConfirmUnverified(null, "User@Example.com", " user@example.com "));
        assertFalse(DsBrokerLink.canConfirmUnverified(null, "one@example.com", "two@example.com"));
        assertFalse(DsBrokerLink.canConfirmUnverified(null, "user@example.com", " "));
        assertFalse(DsBrokerLink.canConfirmUnverified("signed-in", "user@example.com", "user@example.com"));
    }

    @Test
    void copyNamesTheServiceAndTellsThePersonToConfirmTheEmail() {
        assertEquals("Facebook", DsBrokerLink.providerName(" Facebook ", "facebook"));
        assertEquals("facebook", DsBrokerLink.providerName(" ", "facebook"));
        assertEquals("This service", DsBrokerLink.providerName(null, null));
        String missing = DsBrokerLink.refuse("Facebook", null);
        String different = DsBrokerLink.refuse("Facebook", "other@example.com");
        assertTrue(missing.contains("Sign in with your email and enter the code we send"));
        assertTrue(missing.contains("Facebook"));
        assertTrue(different.contains("different email"));
        assertFalse(missing.contains("Google"));
        assertFalse(different.contains("Google"));
        assertTrue(DsBrokerLink.mailIntro("Facebook").contains("confirm you own the address"));
    }
}
