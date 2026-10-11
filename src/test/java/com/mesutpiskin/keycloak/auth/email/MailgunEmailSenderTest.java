package com.mesutpiskin.keycloak.auth.email;

import com.mesutpiskin.keycloak.auth.email.model.EmailMessage;
import com.mesutpiskin.keycloak.auth.email.service.impl.MailgunEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.email.EmailException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("MailgunEmailSender Tests")
class MailgunEmailSenderTest {

    private static final String API_KEY = "key-test123";
    private static final String DOMAIN = "mg.example.com";
    private static final String FROM_EMAIL = "noreply@example.com";
    private static final String FROM_NAME = "Test Sender";

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
    }

    // ── isAvailable ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isAvailable()")
    class IsAvailableTests {

        @Test
        @DisplayName("Returns true when all required fields are present")
        void returnsTrue_whenFullyConfigured() {
            MailgunEmailSender sender = sender(API_KEY, DOMAIN, FROM_EMAIL, FROM_NAME, "US");
            assertTrue(sender.isAvailable());
        }

        @Test
        @DisplayName("Returns false when API key is null")
        void returnsFalse_whenApiKeyNull() {
            assertFalse(sender(null, DOMAIN, FROM_EMAIL, FROM_NAME, "US").isAvailable());
        }

        @Test
        @DisplayName("Returns false when API key is blank")
        void returnsFalse_whenApiKeyBlank() {
            assertFalse(sender("  ", DOMAIN, FROM_EMAIL, FROM_NAME, "US").isAvailable());
        }

        @Test
        @DisplayName("Returns false when domain is null")
        void returnsFalse_whenDomainNull() {
            assertFalse(sender(API_KEY, null, FROM_EMAIL, FROM_NAME, "US").isAvailable());
        }

        @Test
        @DisplayName("Returns false when domain is blank")
        void returnsFalse_whenDomainBlank() {
            assertFalse(sender(API_KEY, "  ", FROM_EMAIL, FROM_NAME, "US").isAvailable());
        }

        @Test
        @DisplayName("Returns false when from email is null")
        void returnsFalse_whenFromEmailNull() {
            assertFalse(sender(API_KEY, DOMAIN, null, FROM_NAME, "US").isAvailable());
        }

        @Test
        @DisplayName("Returns false when from email is blank")
        void returnsFalse_whenFromEmailBlank() {
            assertFalse(sender(API_KEY, DOMAIN, "  ", FROM_NAME, "US").isAvailable());
        }
    }

    // ── getProviderName ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getProviderName() returns 'Mailgun'")
    void getProviderName_returnsMailgun() {
        assertEquals("Mailgun", sender(API_KEY, DOMAIN, FROM_EMAIL, FROM_NAME, "US").getProviderName());
    }

    // ── sendEmail — misconfigured ─────────────────────────────────────────────

    @Test
    @DisplayName("sendEmail() throws EmailException when sender is not configured")
    void sendEmail_throwsWhenNotAvailable() {
        MailgunEmailSender sender = sender(null, DOMAIN, FROM_EMAIL, FROM_NAME, "US");
        EmailMessage msg = message("user@test.com", "Subject", null, null, Map.of());
        assertThrows(EmailException.class, () -> sender.sendEmail(msg));
    }

    // ── sendEmail — HTTP success ──────────────────────────────────────────────

    @Nested
    @DisplayName("sendEmail() — successful HTTP responses")
    class SuccessfulSendTests {

        @Test
        @DisplayName("Sends with plain text body when no HTML provided")
        @SuppressWarnings("unchecked")
        void sendsWithTextBody() throws Exception {
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);

            MailgunEmailSender sender = sender(API_KEY, DOMAIN, FROM_EMAIL, FROM_NAME, "US");
            sender.sendEmail(message("user@test.com", "Code", null, "Your code is 123456", Map.of()));

            verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }

        @Test
        @DisplayName("Sends with HTML body when provided")
        @SuppressWarnings("unchecked")
        void sendsWithHtmlBody() throws Exception {
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);

            MailgunEmailSender sender = sender(API_KEY, DOMAIN, FROM_EMAIL, FROM_NAME, "US");
            sender.sendEmail(message("user@test.com", "Code", "<b>123456</b>", null, Map.of()));

            verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }

        @Test
        @DisplayName("Falls back to template data body when no body set")
        @SuppressWarnings("unchecked")
        void sendsFromTemplateData_whenNoBodiesProvided() throws Exception {
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);

            MailgunEmailSender sender = sender(API_KEY, DOMAIN, FROM_EMAIL, FROM_NAME, "US");
            sender.sendEmail(message("user@test.com", "Code", null, null,
                    Map.of("username", "alice", "code", "654321", "ttl", 300)));

            verify(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }

        @Test
        @DisplayName("Uses EU endpoint when region is EU")
        @SuppressWarnings("unchecked")
        void usesEuEndpoint_whenRegionIsEu() throws Exception {
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);

            when(httpClient.send(argThat((HttpRequest req) ->
                    req.uri().getHost().contains("eu.mailgun")), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);

            MailgunEmailSender sender = sender(API_KEY, DOMAIN, FROM_EMAIL, FROM_NAME, "EU");
            sender.sendEmail(message("user@test.com", "Code", null, "text", Map.of()));

            verify(httpClient).send(argThat((HttpRequest req) ->
                    req.uri().getHost().contains("eu.mailgun")), any(HttpResponse.BodyHandler.class));
        }

        @Test
        @DisplayName("Uses US endpoint when region is US or null")
        @SuppressWarnings("unchecked")
        void usesUsEndpoint_whenRegionIsUs() throws Exception {
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);

            when(httpClient.send(argThat((HttpRequest req) ->
                    req.uri().getHost().equals("api.mailgun.net")), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);

            MailgunEmailSender sender = sender(API_KEY, DOMAIN, FROM_EMAIL, FROM_NAME, null);
            sender.sendEmail(message("user@test.com", "Code", null, "text", Map.of()));

            verify(httpClient).send(argThat((HttpRequest req) ->
                    req.uri().getHost().equals("api.mailgun.net")), any(HttpResponse.BodyHandler.class));
        }
    }

    // ── sendEmail — HTTP errors ───────────────────────────────────────────────

    @Nested
    @DisplayName("sendEmail() — HTTP error responses")
    class ErrorResponseTests {

        @Test
        @DisplayName("Throws EmailException on 4xx response")
        @SuppressWarnings("unchecked")
        void throwsOnClientError() throws Exception {
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(401);
            when(response.body()).thenReturn("Unauthorized");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);

            MailgunEmailSender sender = sender(API_KEY, DOMAIN, FROM_EMAIL, FROM_NAME, "US");
            assertThrows(EmailException.class,
                    () -> sender.sendEmail(message("user@test.com", "Subject", null, "text", Map.of())));
        }

        @Test
        @DisplayName("Throws EmailException on 5xx response")
        @SuppressWarnings("unchecked")
        void throwsOnServerError() throws Exception {
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(500);
            when(response.body()).thenReturn("Internal Server Error");
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);

            MailgunEmailSender sender = sender(API_KEY, DOMAIN, FROM_EMAIL, FROM_NAME, "US");
            assertThrows(EmailException.class,
                    () -> sender.sendEmail(message("user@test.com", "Subject", null, "text", Map.of())));
        }

        @Test
        @DisplayName("Throws EmailException when HttpClient throws IOException")
        @SuppressWarnings("unchecked")
        void throwsOnIOException() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new IOException("Connection refused"));

            MailgunEmailSender sender = sender(API_KEY, DOMAIN, FROM_EMAIL, FROM_NAME, "US");
            assertThrows(EmailException.class,
                    () -> sender.sendEmail(message("user@test.com", "Subject", null, "text", Map.of())));
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MailgunEmailSender sender(String apiKey, String domain, String fromEmail, String fromName, String region) {
        return new MailgunEmailSender(apiKey, domain, fromEmail, fromName, region, httpClient);
    }

    private EmailMessage message(String to, String subject, String html, String text,
            Map<String, Object> templateData) {
        return EmailMessage.builder()
                .to(to)
                .subject(subject)
                .htmlBody(html)
                .textBody(text)
                .templateData(templateData)
                .build();
    }
}
