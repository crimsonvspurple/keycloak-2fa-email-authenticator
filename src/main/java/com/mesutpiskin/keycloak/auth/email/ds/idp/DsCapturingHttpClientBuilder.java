package com.mesutpiskin.keycloak.auth.email.ds.idp;

import org.keycloak.connections.httpclient.HttpClientBuilder;

/**
 * The stock Keycloak client, plus a response interceptor that notes failed
 * upstream calls. Successful responses are not read or copied.
 */
public final class DsCapturingHttpClientBuilder extends HttpClientBuilder {

    public DsCapturingHttpClientBuilder() {
        // Response interceptors run in reverse registration order. Registering
        // first lets content decoding run before we see the body.
        getApacheHttpClientBuilder().addInterceptorFirst(DsIdpUpstreamCapture.INSTANCE);
    }
}
