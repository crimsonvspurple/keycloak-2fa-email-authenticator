package com.mesutpiskin.keycloak.auth.email.ds;

import java.util.Locale;

/**
 * Decides whether a social login may attach to a Keycloak user.
 * A signed-in account-console link stays on that user. A first login links
 * immediately when the matched email is already verified. An unverified
 * match can continue only when both sides name the same email, and only
 * after a code sent to that email is accepted.
 */
public final class DsBrokerLink {

    public static final String SEND_FAILED = "The code could not be sent. Try again in a moment.";

    private DsBrokerLink() {
    }

    public static boolean linkSignedInAccount(String signedInUserId) {
        return signedInUserId != null && !signedInUserId.isBlank();
    }

    public static boolean linkVerifiedEmail(String signedInUserId, boolean emailVerified) {
        return !linkSignedInAccount(signedInUserId) && emailVerified;
    }

    /** An unverified account can be confirmed in this sign-in when the emails match. */
    public static boolean canConfirmUnverified(String signedInUserId, String accountEmail, String brokerEmail) {
        return !linkSignedInAccount(signedInUserId) && sameEmail(accountEmail, brokerEmail);
    }

    public static boolean sameEmail(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        return a != null && a.equals(b);
    }

    public static String providerName(String displayName, String alias) {
        String name = firstText(displayName);
        if (name == null) {
            name = firstText(alias);
        }
        if (name == null) {
            return "This service";
        }
        return name.length() > 40 ? name.substring(0, 40) : name;
    }

    public static String mailIntro(String provider) {
        return provider + " is trying to sign in to DobberSports with this email. "
                + "Enter the code to confirm you own the address and finish signing in. "
                + "If you did not ask for it, you can ignore this message.";
    }

    /**
     * Used when this sign-in cannot email a code. The person has to start from
     * the email page and enter the code sent there before trying the service again.
     */
    public static String refuse(String provider, String brokerEmail) {
        if (normalize(brokerEmail) == null) {
            return provider + " did not share an email address. Sign in with your email and enter the code we send, then try "
                    + provider + " again.";
        }
        return provider + " returned a different email than this account. Sign in with your email and enter the code we send, then try "
                + provider + " again.";
    }

    private static String normalize(String value) {
        String text = firstText(value);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    private static String firstText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
