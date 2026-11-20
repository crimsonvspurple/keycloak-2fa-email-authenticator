package com.mesutpiskin.keycloak.auth.email.ds;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DsAuthMailTest {

    @Test
    void mailUsesARealLinkInsteadOfSlackMarkup() {
        String link = "https://auth.dobbersports.com/realms/ds/login-actions/authenticate?session_code=abc&client_id=gp-web&tab_id=tab&magic=token";
        DsAuthCodes.Mail mail = DsAuthCodes.mail(
                "Use this code to sign in.",
                "123456",
                link);

        assertTrue(mail.text().contains("\n" + link + "\n"));
        assertFalse(mail.text().contains("|Sign in"));
        assertTrue(mail.html().contains("href=\"https://auth.dobbersports.com/realms/ds/login-actions/authenticate?session_code=abc&amp;client_id=gp-web&amp;tab_id=tab&amp;magic=token\""));
        assertTrue(mail.html().contains(">123456<"));
        assertFalse(mail.html().contains("|Sign in"));
    }
}
