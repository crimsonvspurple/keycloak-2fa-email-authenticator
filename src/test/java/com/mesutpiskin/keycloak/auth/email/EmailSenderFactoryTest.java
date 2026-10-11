package com.mesutpiskin.keycloak.auth.email;

import com.mesutpiskin.keycloak.auth.email.model.EmailProviderType;
import com.mesutpiskin.keycloak.auth.email.service.EmailSender;
import com.mesutpiskin.keycloak.auth.email.service.EmailSenderFactory;
import com.mesutpiskin.keycloak.auth.email.service.impl.MailgunEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("EmailSenderFactory Tests")
class EmailSenderFactoryTest {

    private KeycloakSession session;
    private RealmModel realm;
    private UserModel user;

    @BeforeEach
    void setUp() {
        session = mock(KeycloakSession.class);
        realm = mock(RealmModel.class);
        user = mock(UserModel.class);
    }

    @Test
    @DisplayName("Creates MailgunEmailSender when provider type is MAILGUN")
    void createsMailgunSender_whenProviderIsMailgun() {
        Map<String, String> config = Map.of(
                EmailConstants.MAILGUN_API_KEY, "key-test",
                EmailConstants.MAILGUN_DOMAIN, "mg.example.com",
                EmailConstants.MAILGUN_FROM_EMAIL, "noreply@example.com");

        EmailSender sender = EmailSenderFactory.createEmailSender(
                EmailProviderType.MAILGUN, config, session, realm, user);

        assertInstanceOf(MailgunEmailSender.class, sender);
        assertEquals("Mailgun", sender.getProviderName());
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when Mailgun API key is missing")
    void throwsWhenMailgunApiKeyMissing() {
        Map<String, String> config = Map.of(
                EmailConstants.MAILGUN_DOMAIN, "mg.example.com",
                EmailConstants.MAILGUN_FROM_EMAIL, "noreply@example.com");

        assertThrows(IllegalArgumentException.class,
                () -> EmailSenderFactory.createEmailSender(
                        EmailProviderType.MAILGUN, config, session, realm, user));
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when Mailgun domain is missing")
    void throwsWhenMailgunDomainMissing() {
        Map<String, String> config = Map.of(
                EmailConstants.MAILGUN_API_KEY, "key-test",
                EmailConstants.MAILGUN_FROM_EMAIL, "noreply@example.com");

        assertThrows(IllegalArgumentException.class,
                () -> EmailSenderFactory.createEmailSender(
                        EmailProviderType.MAILGUN, config, session, realm, user));
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when Mailgun from email is missing")
    void throwsWhenMailgunFromEmailMissing() {
        Map<String, String> config = Map.of(
                EmailConstants.MAILGUN_API_KEY, "key-test",
                EmailConstants.MAILGUN_DOMAIN, "mg.example.com");

        assertThrows(IllegalArgumentException.class,
                () -> EmailSenderFactory.createEmailSender(
                        EmailProviderType.MAILGUN, config, session, realm, user));
    }

    @Test
    @DisplayName("Mailgun sender is available with valid configuration")
    void mailgunSenderIsAvailable_withValidConfig() {
        Map<String, String> config = Map.of(
                EmailConstants.MAILGUN_API_KEY, "key-test",
                EmailConstants.MAILGUN_DOMAIN, "mg.example.com",
                EmailConstants.MAILGUN_FROM_EMAIL, "noreply@example.com",
                EmailConstants.MAILGUN_REGION, "EU");

        EmailSender sender = EmailSenderFactory.createEmailSender(
                EmailProviderType.MAILGUN, config, session, realm, user);

        assertTrue(sender.isAvailable());
    }
}
