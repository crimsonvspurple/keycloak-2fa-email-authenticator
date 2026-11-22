package com.mesutpiskin.keycloak.auth.email.ds.idp;

import org.jboss.logging.Logger;

/**
 * One structured line per social sign-in failure. The category is routed to
 * the reviewer file even though a 4xx from the provider is logged at WARN.
 */
public final class DsIdpFailureLog {

    public static final String CATEGORY = "com.mesutpiskin.keycloak.auth.email.ds.idp";
    public static final Logger LOG = Logger.getLogger(CATEGORY);

    private DsIdpFailureLog() {
    }

    public static String line(String provider, String realm, String clientId, String stage, int upstreamStatus, String snippet) {
        return "IdP sign-in failed"
                + " provider=" + DsIdpSnippets.safeToken(provider, 80)
                + " realm=" + DsIdpSnippets.safeToken(realm, 80)
                + " client_id=" + DsIdpSnippets.safeToken(clientId, 80)
                + " stage=" + (stage == null || stage.isBlank() ? DsIdpStages.OTHER : stage)
                + " upstream_status=" + (upstreamStatus > 0 ? Integer.toString(upstreamStatus) : "-")
                + " snippet=\"" + (snippet == null ? "" : snippet) + "\"";
    }

    public static void log(String provider, String realm, String clientId, String stage, int upstreamStatus, String snippet) {
        String message = line(provider, realm, clientId, stage, upstreamStatus, snippet);
        if (upstreamStatus >= 500) {
            LOG.error(message);
        } else {
            LOG.warn(message);
        }
    }
}
