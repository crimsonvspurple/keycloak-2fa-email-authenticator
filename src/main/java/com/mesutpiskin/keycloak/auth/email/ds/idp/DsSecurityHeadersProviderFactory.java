package com.mesutpiskin.keycloak.auth.email.ds.idp;

import org.keycloak.headers.SecurityHeadersProvider;
import org.keycloak.headers.SecurityHeadersProviderFactory;
import org.keycloak.models.KeycloakSession;

/**
 * Same provider id as Keycloak's built-in header provider, with a higher
 * order so this one is selected. Header behaviour is inherited.
 */
public final class DsSecurityHeadersProviderFactory implements SecurityHeadersProviderFactory {

    @Override
    public SecurityHeadersProvider create(KeycloakSession session) {
        return new DsSecurityHeadersProvider(session);
    }

    @Override
    public String getId() {
        return "default";
    }

    @Override
    public int order() {
        return 1;
    }
}
