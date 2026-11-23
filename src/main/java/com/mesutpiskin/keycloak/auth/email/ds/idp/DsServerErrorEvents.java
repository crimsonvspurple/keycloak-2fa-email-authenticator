package com.mesutpiskin.keycloak.auth.email.ds.idp;

import java.util.Set;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;

/**
 * Decides which Keycloak {@code *_ERROR} events belong in the reviewer file.
 * Wrong passwords, expired codes, and other ordinary user mistakes stay out.
 */
public final class DsServerErrorEvents {

    public static final String CATEGORY = "com.mesutpiskin.keycloak.auth.email.ds.errors";
    public static final Logger LOG = Logger.getLogger(CATEGORY);

    static final Set<String> USER_ERRORS = Set.of(
            "invalid_user_credentials",
            "user_not_found",
            "user_disabled",
            "user_temporarily_disabled",
            "invalid_code",
            "expired_code",
            "invalid_token",
            "invalid_token_type",
            "cookie_not_found",
            "session_expired",
            "user_session_not_found",
            "invalid_authentication_session",
            "different_user_authenticated",
            "different_user_authenticating",
            "already_logged_in",
            "not_logged_in",
            "consent_denied",
            "rejected_by_user",
            "access_denied",
            "username_missing",
            "username_in_use",
            "email_in_use",
            "email_already_verified",
            "password_confirm_error",
            "password_missing",
            "password_rejected",
            "invalid_email",
            "invalid_form",
            "invalid_input",
            "invalid_registration",
            "expired_oauth2_device_code",
            "invalid_oauth2_device_code",
            "invalid_oauth2_user_code",
            "slow_down",
            "not_allowed",
            "resolve_required_actions",
            "generic_authentication_error",
            "credential_not_found",
            "missing_credential_id",
            "missing_tx_code",
            "invalid_tx_code",
            "code_verifier_missing",
            "invalid_code_verifier",
            "pkce_verification_failed",
            "invalid_code_challenge_method",
            "identity_provider_link_confirmed_already",
            "federated_identity_account_exists",
            "user_org_member_already");

    private DsServerErrorEvents() {
    }

    public static boolean include(String type, String error) {
        if (type == null || !type.endsWith("_ERROR")) {
            return false;
        }
        return error == null || error.isBlank() || !USER_ERRORS.contains(error);
    }

    public static String line(String type, String realm, String clientId, String error, String provider, String userId) {
        return "Keycloak server error"
                + " type=" + DsIdpSnippets.safeToken(type, 80)
                + " realm=" + DsIdpSnippets.safeToken(realm, 80)
                + " client_id=" + DsIdpSnippets.safeToken(clientId, 80)
                + " error=" + DsIdpSnippets.safeToken(error, 80)
                + " provider=" + DsIdpSnippets.safeToken(provider, 80)
                + " user_id=" + safeUser(userId);
    }

    public static void log(Event event) {
        if (event == null || event.getType() == null || !include(event.getType().name(), event.getError())) {
            return;
        }
        String provider = event.getDetails() == null ? null : event.getDetails().get("identity_provider");
        LOG.error(line(event.getType().name(), event.getRealmName(), event.getClientId(), event.getError(), provider,
                event.getUserId()));
    }

    public static void logAdmin(String operation, String realm, String clientId, String error, String resourcePath) {
        if (error == null || error.isBlank() || USER_ERRORS.contains(error)) {
            return;
        }
        String path = resourcePath == null ? "-" : resourcePath;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        LOG.error("Keycloak admin error"
                + " operation=" + DsIdpSnippets.safeToken(operation, 40)
                + " realm=" + DsIdpSnippets.safeToken(realm, 80)
                + " client_id=" + DsIdpSnippets.safeToken(clientId, 80)
                + " error=" + DsIdpSnippets.safeToken(error, 80)
                + " resource=" + DsIdpSnippets.safeToken(path.replace('/', '.'), 120));
    }

    private static String safeUser(String userId) {
        if (userId == null || userId.isBlank() || userId.indexOf('@') >= 0) {
            return "-";
        }
        return DsIdpSnippets.safeToken(userId, 80);
    }
}
