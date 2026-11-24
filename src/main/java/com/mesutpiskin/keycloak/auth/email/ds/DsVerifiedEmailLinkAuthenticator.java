package com.mesutpiskin.keycloak.auth.email.ds;

import jakarta.ws.rs.core.Response;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Replaces idp-auto-link. create-if-unique has already found the email.
 * A verified email links at once. An unverified email gets a code at that
 * same address; the identity attaches only after the code is accepted.
 */
public class DsVerifiedEmailLinkAuthenticator extends AbstractIdpAuthenticator {

    private static final String SUBJECT = "Your DobberSports confirmation code";

    @Override
    protected void authenticateImpl(AuthenticationFlowContext context, SerializedBrokeredIdentityContext serializedCtx, BrokeredIdentityContext brokerContext) {
        UserModel existing = getExistingUser(context.getSession(), context.getRealm(), context.getAuthenticationSession());
        String provider = providerName(brokerContext);
        String signedInId = signedInId(context);
        if (DsBrokerLink.linkVerifiedEmail(signedInId, existing.isEmailVerified())) {
            context.setUser(existing);
            context.success();
            return;
        }
        if (!DsBrokerLink.canConfirmUnverified(signedInId, existing.getEmail(), brokerContext.getEmail())) {
            refuse(context, provider, brokerContext.getEmail());
            return;
        }
        context.setUser(existing);
        if (context.getAuthenticationSession().getAuthNote(DsAuthCodes.CODE_HASH) != null) {
            context.challenge(form(context, provider, null));
            return;
        }
        send(context, provider);
    }

    @Override
    protected void actionImpl(AuthenticationFlowContext context, SerializedBrokeredIdentityContext serializedCtx, BrokeredIdentityContext brokerContext) {
        // The email link is a GET of the action URL, so Keycloak calls action().
        String provider = providerName(brokerContext);
        if (DsAuthCodes.hasMagic(context)) {
            if (DsAuthCodes.magicMatches(context)) {
                finish(context, brokerContext);
                return;
            }
            context.challenge(form(context, provider, "That link is no longer valid. Enter the code instead."));
            return;
        }
        if (!Turnstile.allows(context, DsAuthCodes.formValue(context, "cf-turnstile-response"), Turnstile.OTP)) {
            context.challenge(form(context, provider, Turnstile.REJECTED));
            return;
        }
        if (DsAuthCodes.pressed(context, "resend")) {
            int wait = DsAuthCodes.resendWaitSeconds(context);
            if (wait > 0) {
                context.challenge(form(context, provider, "You can email a new code in " + wait + " seconds."));
                return;
            }
            send(context, provider);
            return;
        }
        String error = DsAuthCodes.rejectCode(context, DsAuthCodes.formValue(context, "code"));
        if (error != null) {
            context.challenge(form(context, provider, error));
            return;
        }
        DsAuthCodes.clear(context);
        finish(context, brokerContext);
    }

    private void finish(AuthenticationFlowContext context, BrokeredIdentityContext brokerContext) {
        UserModel existing = getExistingUser(context.getSession(), context.getRealm(), context.getAuthenticationSession());
        if (!DsBrokerLink.canConfirmUnverified(signedInId(context), existing.getEmail(), brokerContext.getEmail())) {
            refuse(context, providerName(brokerContext), brokerContext.getEmail());
            return;
        }
        if (!existing.isEmailVerified()) {
            existing.setEmailVerified(true);
        }
        context.setUser(existing);
        context.success();
    }

    private void send(AuthenticationFlowContext context, String provider) {
        boolean sent = DsAuthCodes.send(context, "broker-confirm", SUBJECT, DsBrokerLink.mailIntro(provider));
        context.challenge(form(context, provider, sent ? null : DsBrokerLink.SEND_FAILED));
    }

    private void refuse(AuthenticationFlowContext context, String provider, String brokerEmail) {
        sendFailureChallenge(context, Response.Status.BAD_REQUEST, Errors.FEDERATED_IDENTITY_EXISTS,
                DsBrokerLink.refuse(provider, brokerEmail), AuthenticationFlowError.IDENTITY_PROVIDER_ERROR);
    }

    private Response form(AuthenticationFlowContext context, String provider, String error) {
        UserModel user = context.getUser();
        LoginFormsProvider page = context.form()
                .setAttribute("dsIdpName", provider)
                .setAttribute("dsEmail", user == null ? "" : user.getEmail())
                .setAttribute("dsResendWait", DsAuthCodes.resendWaitSeconds(context))
                .setAttribute("dsTurnstileSiteKey", Turnstile.siteKeyFor(context));
        if (error != null) {
            page.setError(error);
        }
        return page.createForm("ds-broker-code.ftl");
    }

    private static String signedInId(AuthenticationFlowContext context) {
        UserModel signedIn = context.getAuthenticationSession().getAuthenticatedUser();
        return signedIn == null ? null : signedIn.getId();
    }

    private static String providerName(BrokeredIdentityContext brokerContext) {
        IdentityProviderModel idp = brokerContext == null ? null : brokerContext.getIdpConfig();
        if (idp == null) {
            return DsBrokerLink.providerName(null, null);
        }
        return DsBrokerLink.providerName(idp.getDisplayName(), idp.getAlias());
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }
}
