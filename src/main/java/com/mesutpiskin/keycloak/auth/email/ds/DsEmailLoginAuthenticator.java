package com.mesutpiskin.keycloak.auth.email.ds;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.WebAuthnConditionalUIAuthenticator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.events.Errors;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;

/**
 * Email first. The next page is a password, or a code and a same-browser link.
 * Sign-in does not create an account. A missing password is not a required action.
 */
public class DsEmailLoginAuthenticator implements Authenticator {

    static final String STEP = "ds.step";

    private final WebAuthnConditionalUIAuthenticator passkeys;

    public DsEmailLoginAuthenticator(KeycloakSession session) {
        this.passkeys = new WebAuthnConditionalUIAuthenticator(session, this::emailForm);
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        if (DsAuthCodes.magicMatches(context)) {
            context.success();
            return;
        }
        String step = context.getAuthenticationSession().getAuthNote(STEP);
        if ("code".equals(step) && context.getUser() != null) {
            context.challenge(codeForm(context));
            return;
        }
        if ("credential".equals(step) && context.getUser() != null) {
            context.challenge(credentialForm(context));
            return;
        }
        context.challenge(emailForm(context));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        if (form.containsKey("restart")) {
            restart(context);
            return;
        }
        if (passkeys.isPasskeysEnabled()
                && (form.containsKey("clientDataJSON") || form.containsKey("error"))) {
            passkeys.action(context);
            return;
        }
        String step = context.getAuthenticationSession().getAuthNote(STEP);
        if ("code".equals(step)) {
            if (form.containsKey("resend")) {
                sendCode(context);
                return;
            }
            String error = DsAuthCodes.rejectCode(context, form.getFirst("code"));
            if (error == null) {
                context.success();
                return;
            }
            context.form().setError(error);
            context.challenge(codeForm(context));
            return;
        }
        if (form.containsKey("email-code")) {
            sendCode(context);
            return;
        }
        if ("credential".equals(step) && form.containsKey("login")) {
            checkPassword(context);
            return;
        }
        lookup(context, form.getFirst("username"));
    }

    private void lookup(AuthenticationFlowContext context, String raw) {
        String email = raw == null ? "" : raw.trim();
        if (!email.contains("@") || email.length() > 320) {
            context.form().setError("Enter the email on the account.");
            context.challenge(emailForm(context));
            return;
        }
        UserModel user = KeycloakModelUtils.findUserByNameOrEmail(context.getSession(), context.getRealm(), email);
        if (user == null || !user.isEnabled()) {
            context.getEvent().error(Errors.USER_NOT_FOUND);
            context.form().setError("No DobberSports account uses that email.");
            context.challenge(emailForm(context));
            return;
        }
        context.setUser(user);
        context.getAuthenticationSession().setAuthNote(STEP, "credential");
        context.challenge(credentialForm(context));
    }

    private void checkPassword(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            restart(context);
            return;
        }
        String password = DsAuthCodes.formValue(context, "password");
        boolean hasPassword = user.credentialManager().isConfiguredFor(CredentialModel.PASSWORD);
        if (!hasPassword) {
            context.form().setError("This account has no password. Email yourself a code, or set a password.");
            context.challenge(credentialForm(context));
            return;
        }
        if (!user.credentialManager().isValid(UserCredentialModel.password(password))) {
            context.getEvent().user(user);
            context.getEvent().error(Errors.INVALID_USER_CREDENTIALS);
            context.form().setError("Wrong password.");
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, credentialForm(context));
            return;
        }
        context.success();
    }

    private void sendCode(AuthenticationFlowContext context) {
        if (context.getUser() == null) {
            restart(context);
            return;
        }
        boolean sent = DsAuthCodes.send(
                context,
                "login-code",
                "Your DobberSports sign-in code",
                "Use this code to sign in to DobberSports. If you did not ask for it, you can ignore this message.");
        if (!sent) {
            context.form().setError("The code could not be sent. Try again in a moment.");
            context.challenge(credentialForm(context));
            return;
        }
        context.getAuthenticationSession().setAuthNote(STEP, "code");
        context.challenge(codeForm(context));
    }

    private void restart(AuthenticationFlowContext context) {
        context.clearUser();
        context.getAuthenticationSession().removeAuthNote(STEP);
        context.getAuthenticationSession().removeAuthNote(DsAuthCodes.CODE_HASH);
        context.getAuthenticationSession().removeAuthNote(DsAuthCodes.MAGIC_HASH);
        context.getAuthenticationSession().removeAuthNote(DsAuthCodes.EXPIRES);
        context.getAuthenticationSession().removeAuthNote(DsAuthCodes.ATTEMPTS);
        context.challenge(emailForm(context));
    }

    private Response emailForm(AuthenticationFlowContext context) {
        if (passkeys.isPasskeysEnabled()) {
            passkeys.fillContextForm(context);
        }
        return context.form().setAttribute("dsStep", "email").createForm("ds-login.ftl");
    }

    private Response credentialForm(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        boolean hasPassword = user != null && user.credentialManager().isConfiguredFor(CredentialModel.PASSWORD);
        return context.form()
                .setAttribute("dsStep", "credential")
                .setAttribute("dsEmail", user == null ? "" : user.getEmail())
                .setAttribute("hasPassword", hasPassword)
                .createForm("ds-login.ftl");
    }

    private Response codeForm(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        return context.form()
                .setAttribute("dsStep", "code")
                .setAttribute("dsEmail", user == null ? "" : user.getEmail())
                .createForm("ds-login.ftl");
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // A user with no password signs in with a code. Setting a password stays optional.
    }

    @Override
    public void close() {
    }
}
