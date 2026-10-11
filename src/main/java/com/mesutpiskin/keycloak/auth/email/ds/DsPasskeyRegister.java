package com.mesutpiskin.keycloak.auth.email.ds;

import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.requiredactions.WebAuthnPasswordlessRegister;
import org.keycloak.models.KeycloakSession;

import com.webauthn4j.verifier.attestation.trustworthiness.certpath.CertPathTrustworthinessVerifier;

/**
 * The stock passkey registration page, plus a skip so the checkbox is optional.
 */
public class DsPasskeyRegister extends WebAuthnPasswordlessRegister {

    public DsPasskeyRegister(KeycloakSession session, CertPathTrustworthinessVerifier verifier) {
        super(session, verifier);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        if (context.getHttpRequest().getDecodedFormParameters().containsKey("skip")) {
            context.success();
            return;
        }
        super.processAction(context);
    }
}
