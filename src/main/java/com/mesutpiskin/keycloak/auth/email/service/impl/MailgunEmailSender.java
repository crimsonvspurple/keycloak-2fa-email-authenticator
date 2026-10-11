package com.mesutpiskin.keycloak.auth.email.service.impl;

import com.mesutpiskin.keycloak.auth.email.model.EmailMessage;
import com.mesutpiskin.keycloak.auth.email.service.EmailSender;
import org.jboss.logging.Logger;
import org.keycloak.email.EmailException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mailgun email sender implementation using Mailgun's REST API.
 * <p>
 * Sends emails via Mailgun's HTTP API using Java's built-in {@link HttpClient}.
 * Supports both US and EU regions. No external SDK dependency is required.
 * </p>
 *
 * <p>
 * Configuration requirements:
 * <ul>
 * <li>Mailgun API Key (required)</li>
 * <li>Mailgun Domain (required) — the sending domain configured in Mailgun</li>
 * <li>From Email Address (required)</li>
 * <li>From Name (optional)</li>
 * <li>Region — "US" (default) or "EU" (optional)</li>
 * </ul>
 * </p>
 *
 * @author Mesut Pişkin
 * @version 26.4.0
 * @since 26.4.0
 */
public class MailgunEmailSender implements EmailSender {

    private static final Logger logger = Logger.getLogger(MailgunEmailSender.class);

    private static final String API_BASE_US = "https://api.mailgun.net/v3";
    private static final String API_BASE_EU = "https://api.eu.mailgun.net/v3";

    private final String apiKey;
    private final String domain;
    private final String fromEmail;
    private final String fromName;
    private final String region;

    private final HttpClient httpClient;

    /**
     * Constructs a new MailgunEmailSender.
     *
     * @param apiKey    the Mailgun API key
     * @param domain    the Mailgun sending domain (e.g., mg.example.com)
     * @param fromEmail the sender email address
     * @param fromName  the sender display name (optional, falls back to fromEmail)
     * @param region    the API region: "US" or "EU" (null defaults to "US")
     */
    public MailgunEmailSender(String apiKey, String domain, String fromEmail, String fromName, String region) {
        this.apiKey = apiKey;
        this.domain = domain;
        this.fromEmail = fromEmail;
        this.fromName = fromName != null && !fromName.isBlank() ? fromName : fromEmail;
        this.region = (region != null && region.trim().equalsIgnoreCase("EU")) ? "EU" : "US";
        this.httpClient = HttpClient.newHttpClient();
    }

    public MailgunEmailSender(String apiKey, String domain, String fromEmail, String fromName, String region,
            HttpClient httpClient) {
        this.apiKey = apiKey;
        this.domain = domain;
        this.fromEmail = fromEmail;
        this.fromName = fromName != null && !fromName.isBlank() ? fromName : fromEmail;
        this.region = (region != null && region.trim().equalsIgnoreCase("EU")) ? "EU" : "US";
        this.httpClient = httpClient;
    }

    @Override
    public void sendEmail(EmailMessage message) throws EmailException {
        if (!isAvailable()) {
            throw new EmailException("Mailgun is not properly configured");
        }

        String apiBase = "EU".equals(region) ? API_BASE_EU : API_BASE_US;
        String url = apiBase + "/" + domain + "/messages";

        String from = fromName.equals(fromEmail)
                ? fromEmail
                : fromName + " <" + fromEmail + ">";

        Map<String, String> formFields = new LinkedHashMap<>();
        formFields.put("from", from);
        formFields.put("to", message.getTo());
        formFields.put("subject", message.getSubject());

        if (message.getHtmlBody() != null && !message.getHtmlBody().isBlank()) {
            formFields.put("html", message.getHtmlBody());
        } else if (message.getTextBody() != null && !message.getTextBody().isBlank()) {
            formFields.put("text", message.getTextBody());
        } else {
            formFields.put("text", buildTextFromTemplateData(message));
        }

        String body = formFields.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        String credentials = Base64.getEncoder()
                .encodeToString(("api:" + apiKey).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.debugf("Email sent successfully via Mailgun (%s) to %s (status: %d)",
                        region, message.getTo(), response.statusCode());
            } else {
                String errorMsg = String.format(
                        "Mailgun API returned error status %d for recipient %s: %s",
                        response.statusCode(), message.getTo(), response.body());
                logger.error(errorMsg);
                throw new EmailException(errorMsg);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String errorMsg = String.format("Failed to send email via Mailgun to %s", message.getTo());
            logger.errorf(e, errorMsg);
            throw new EmailException(errorMsg, e);
        }
    }

    @Override
    public String getProviderName() {
        return "Mailgun";
    }

    @Override
    public boolean isAvailable() {
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("Mailgun API key is not configured");
            return false;
        }
        if (domain == null || domain.isBlank()) {
            logger.warn("Mailgun domain is not configured");
            return false;
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            logger.warn("Mailgun from email is not configured");
            return false;
        }
        return true;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String buildTextFromTemplateData(EmailMessage message) {
        StringBuilder sb = new StringBuilder();

        Object username = message.getTemplateData().get("username");
        Object code = message.getTemplateData().get("code");
        Object ttl = message.getTemplateData().get("ttl");

        if (username != null) {
            sb.append("Hello ").append(username).append(",\n\n");
        }
        if (code != null) {
            sb.append("Your verification code is: ").append(code).append("\n\n");
        }
        if (ttl != null) {
            sb.append("This code will expire in ").append(ttl).append(" seconds.\n\n");
        }
        sb.append("If you did not request this code, please ignore this email.");

        return sb.toString();
    }
}
