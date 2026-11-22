package com.mesutpiskin.keycloak.auth.email.ds.idp;

import org.keycloak.connections.httpclient.DefaultHttpClientFactory;
import org.keycloak.connections.httpclient.HttpClientBuilder;
import org.keycloak.models.KeycloakSession;

/**
 * Replaces Keycloak's default HTTP client so every social provider, including
 * the separate Apple provider, reports upstream failures through one hook.
 * Timeouts, trust, and pooling stay on the stock factory.
 */
public final class DsHttpClientFactory extends DefaultHttpClientFactory {

    @Override
    public int order() {
        return 1;
    }

    @Override
    protected HttpClientBuilder newHttpClientBuilder(KeycloakSession session) {
        return new DsCapturingHttpClientBuilder();
    }
}
