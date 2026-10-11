package com.mesutpiskin.keycloak.auth.email.ds;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.truststore.TruststoreProvider;

import com.webauthn4j.anchor.KeyStoreTrustAnchorRepository;
import com.webauthn4j.verifier.attestation.trustworthiness.certpath.CertPathTrustworthinessVerifier;
import com.webauthn4j.verifier.attestation.trustworthiness.certpath.DefaultCertPathTrustworthinessVerifier;
import com.webauthn4j.verifier.attestation.trustworthiness.certpath.NullCertPathTrustworthinessVerifier;

public class DsPasskeyRegisterFactory implements RequiredActionFactory {

    public static final String PROVIDER_ID = "ds-passkey-register";

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        TruststoreProvider truststore = session.getProvider(TruststoreProvider.class);
        CertPathTrustworthinessVerifier verifier;
        if (truststore == null || truststore.getTruststore() == null) {
            verifier = new NullCertPathTrustworthinessVerifier();
        } else {
            verifier = new DefaultCertPathTrustworthinessVerifier(
                    new KeyStoreTrustAnchorRepository(truststore.getTruststore()));
        }
        return new DsPasskeyRegister(session, verifier);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayText() {
        return "Create a passkey";
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
