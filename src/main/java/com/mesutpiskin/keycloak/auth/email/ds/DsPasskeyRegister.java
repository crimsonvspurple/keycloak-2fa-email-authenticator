package com.mesutpiskin.keycloak.auth.email.ds;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;

import org.jboss.logging.Logger;
import org.keycloak.WebAuthnConstants;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.requiredactions.WebAuthnPasswordlessRegister;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;

import com.webauthn4j.verifier.attestation.trustworthiness.certpath.CertPathTrustworthinessVerifier;

/**
 * The stock passkey registration page, plus a skip so the checkbox is optional.
 * The browser chooses the passkey name. This rewrites it when that name is
 * already stored, so registration does not stop on a duplicate-name error.
 */
public class DsPasskeyRegister extends WebAuthnPasswordlessRegister {

    private static final Logger LOG = Logger.getLogger(DsPasskeyRegister.class);

    public DsPasskeyRegister(KeycloakSession session, CertPathTrustworthinessVerifier verifier) {
        super(session, verifier);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        if (form.containsKey("skip")) {
            context.success();
            return;
        }
        applyUniqueLabel(context, form);
        super.processAction(context);
    }

    static void applyUniqueLabel(RequiredActionContext context, MultivaluedMap<String, String> form) {
        if (leavesTheCeremony(form)) {
            return;
        }
        String label = PasskeyLabels.uniqueLabel(
                form.getFirst(WebAuthnConstants.AUTHENTICATOR_LABEL),
                userAgent(context),
                existingLabels(context));
        try {
            form.putSingle(WebAuthnConstants.AUTHENTICATOR_LABEL, label);
        } catch (UnsupportedOperationException ex) {
            LOG.warn("Passkey name could not be written onto the registration form.");
        }
    }

    private static boolean leavesTheCeremony(MultivaluedMap<String, String> form) {
        String retry = form.getFirst(WebAuthnConstants.IS_SET_RETRY);
        if (retry != null && !retry.isEmpty()) {
            return true;
        }
        String error = form.getFirst(WebAuthnConstants.ERROR);
        return error != null && !error.isEmpty();
    }

    private static String userAgent(RequiredActionContext context) {
        HttpHeaders headers = context.getHttpRequest().getHttpHeaders();
        if (headers == null) {
            return null;
        }
        return headers.getHeaderString(HttpHeaders.USER_AGENT);
    }

    private static List<String> existingLabels(RequiredActionContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            return List.of();
        }
        try {
            SubjectCredentialManager manager = user.credentialManager();
            if (manager == null) {
                return List.of();
            }
            // Same-type names collide for local users. Federated users collide
            // across types. Either match is enough to pick another suffix.
            try (Stream<CredentialModel> credentials = manager.getStoredCredentialsStream()) {
                return credentials
                        .map(CredentialModel::getUserLabel)
                        .filter(Objects::nonNull)
                        .toList();
            }
        } catch (RuntimeException ex) {
            LOG.warn("Could not read existing passkey names. A new name will be chosen without that list.");
            return List.of();
        }
    }
}
