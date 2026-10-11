package com.mesutpiskin.keycloak.auth.email.service;

import com.mesutpiskin.keycloak.auth.email.model.EmailProviderType;
import com.mesutpiskin.keycloak.auth.email.service.impl.KeycloakEmailSender;
import com.mesutpiskin.keycloak.auth.email.service.impl.MailgunEmailSender;
import com.mesutpiskin.keycloak.auth.email.service.impl.SendGridEmailSender;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.Map;

/**
 * Factory for creating EmailSender instances based on configuration.
 * <p>
 * This factory implements the Strategy Pattern to provide the appropriate
 * email sender implementation based on the configured provider type.
 * </p>
 *
 * @author Mesut Pişkin
 * @version 26.1.1
 * @since 1.1.0
 */
public final class EmailSenderFactory {

    private static final Logger logger = Logger.getLogger(EmailSenderFactory.class);

    /**
     * Private constructor to prevent instantiation.
     */
    private EmailSenderFactory() {
        throw new UnsupportedOperationException("EmailSenderFactory is a utility class and cannot be instantiated");
    }

    /**
     * Creates an EmailSender instance based on the provided configuration.
     *
     * @param providerType the type of email provider to create
     * @param config       configuration map containing provider-specific settings
     * @param session      the Keycloak session
     * @param realm        the realm model
     * @param user         the user to send email to
     * @return an EmailSender instance configured for the specified provider
     * @throws IllegalArgumentException if the provider type is not supported or
     *                                  required configuration is missing
     */
    public static EmailSender createEmailSender(
            EmailProviderType providerType,
            Map<String, String> config,
            KeycloakSession session,
            RealmModel realm,
            UserModel user) {

        logger.debugf("Creating email sender for provider type: %s", providerType.getDisplayName());

        switch (providerType) {
            case SENDGRID:
                return createSendGridSender(config);

            case AWS_SES:
                return createAwsSesSender(config);

            case MAILGUN:
                return createMailgunSender(config);

            case KEYCLOAK:
            default:
                return new KeycloakEmailSender(session, realm, user);
        }
    }

    /**
     * Creates a SendGrid email sender with the provided configuration.
     *
     * @param config configuration map containing SendGrid settings
     * @return a configured SendGridEmailSender instance
     * @throws IllegalArgumentException if required SendGrid configuration is
     *                                  missing
     */
    private static EmailSender createSendGridSender(Map<String, String> config) {
        String apiKey = config.get("sendgridApiKey");
        String fromEmail = config.get("sendgridFromEmail");
        String fromName = config.get("sendgridFromName");

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("SendGrid API key is required but not configured");
        }

        if (fromEmail == null || fromEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("SendGrid from email is required but not configured");
        }

        logger.infof("Creating SendGrid email sender with from address: %s", fromEmail);
        return new SendGridEmailSender(apiKey, fromEmail, fromName);
    }

    /**
     * Creates an AWS SES email sender with the provided configuration.
     *
     * @param config configuration map containing AWS SES settings
     * @return a configured AwsSesEmailSender instance
     * @throws IllegalArgumentException if required AWS SES configuration is missing
     */
    private static EmailSender createAwsSesSender(Map<String, String> config) {
        String region = config.get("awsSesRegion");
        String accessKeyId = config.get("awsAccessKeyId");
        String secretAccessKey = config.get("awsSecretAccessKey");
        String fromEmail = config.get("awsSesFromEmail");
        String fromName = config.get("awsSesFromName");

        if (region == null || region.trim().isEmpty()) {
            throw new IllegalArgumentException("AWS SES region is required but not configured");
        }

        if (accessKeyId == null || accessKeyId.trim().isEmpty()) {
            throw new IllegalArgumentException("AWS Access Key ID is required but not configured");
        }

        if (secretAccessKey == null || secretAccessKey.trim().isEmpty()) {
            throw new IllegalArgumentException("AWS Secret Access Key is required but not configured");
        }

        if (fromEmail == null || fromEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("AWS SES from email is required but not configured");
        }

        logger.infof("Creating AWS SES email sender in region %s with from address: %s", region, fromEmail);
        return new com.mesutpiskin.keycloak.auth.email.service.impl.AwsSesEmailSender(
                region, accessKeyId, secretAccessKey, fromEmail, fromName);
    }

    /**
     * Creates a Mailgun email sender with the provided configuration.
     *
     * @param config configuration map containing Mailgun settings
     * @return a configured MailgunEmailSender instance
     * @throws IllegalArgumentException if required Mailgun configuration is missing
     */
    private static EmailSender createMailgunSender(Map<String, String> config) {
        String apiKey = config.get("mailgunApiKey");
        String domain = config.get("mailgunDomain");
        String fromEmail = config.get("mailgunFromEmail");
        String fromName = config.get("mailgunFromName");
        String region = config.getOrDefault("mailgunRegion", "US");

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Mailgun API key is required but not configured");
        }
        if (domain == null || domain.trim().isEmpty()) {
            throw new IllegalArgumentException("Mailgun domain is required but not configured");
        }
        if (fromEmail == null || fromEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Mailgun from email is required but not configured");
        }

        logger.infof("Creating Mailgun email sender for domain %s (%s region) with from address: %s",
                domain, region, fromEmail);
        return new MailgunEmailSender(apiKey, domain, fromEmail, fromName, region);
    }

    /**
     * Helper method to determine if fallback to Keycloak SMTP is enabled.
     *
     * @param config configuration map
     * @return true if fallback is enabled, false otherwise
     */
    public static boolean isFallbackEnabled(Map<String, String> config) {
        if (config == null) {
            return true; // Default to enabled
        }

        String fallbackValue = config.get("enableFallback");
        if (fallbackValue == null || fallbackValue.trim().isEmpty()) {
            return true; // Default to enabled
        }

        return Boolean.parseBoolean(fallbackValue);
    }
}
