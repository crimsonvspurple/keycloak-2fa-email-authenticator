package com.mesutpiskin.keycloak.auth.email.ds;

/**
 * Decides whether a Google or X login may attach to a Keycloak user.
 * A signed-in account-console link stays on that user. A first login links
 * only when the matched email is already verified.
 */
public final class DsBrokerLink {

    private DsBrokerLink() {
    }

    public static boolean linkSignedInAccount(String signedInUserId) {
        return signedInUserId != null && !signedInUserId.isBlank();
    }

    public static boolean linkVerifiedEmail(String signedInUserId, boolean emailVerified) {
        return !linkSignedInAccount(signedInUserId) && emailVerified;
    }
}
