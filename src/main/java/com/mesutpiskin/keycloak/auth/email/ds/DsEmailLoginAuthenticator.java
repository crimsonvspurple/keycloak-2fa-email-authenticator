package com.mesutpiskin.keycloak.auth.email.ds;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.keycloak.WebAuthnConstants;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.WebAuthnConditionalUIAuthenticator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
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
            context.challenge(codeForm(context, null));
            return;
        }
        if ("credential".equals(step) && context.getUser() != null) {
            context.challenge(credentialForm(context, null));
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
            context.challenge(codeForm(context, error));
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
            context.challenge(emailForm(context, "Enter the email on the account."));
            return;
        }
        UserModel user = KeycloakModelUtils.findUserByNameOrEmail(context.getSession(), context.getRealm(), email);
        if (user == null || !user.isEnabled()) {
            context.getEvent().error(Errors.USER_NOT_FOUND);
            context.challenge(emailForm(context, "No DobberSports account uses that email."));
            return;
        }
        context.setUser(user);
        context.getAuthenticationSession().setAuthNote(STEP, "credential");
        context.challenge(credentialForm(context, null));
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
            context.challenge(credentialForm(context, "This account has no password. Email yourself a code, or set a password."));
            return;
        }
        if (!user.credentialManager().isValid(UserCredentialModel.password(password))) {
            context.getEvent().user(user);
            context.getEvent().error(Errors.INVALID_USER_CREDENTIALS);
            context.failureChallenge(
                    AuthenticationFlowError.INVALID_CREDENTIALS,
                    credentialForm(context, "Wrong password."));
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
            context.challenge(credentialForm(context, "The code could not be sent. Try again in a moment."));
            return;
        }
        context.getAuthenticationSession().setAuthNote(STEP, "code");
        context.challenge(codeForm(context, null));
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
        return emailForm(context, null);
    }

    private Response emailForm(AuthenticationFlowContext context, String error) {
        // createForm does not add the login bean, and a second form() call drops attributes.
        LoginFormsProvider form = context.form();
        if (passkeys.isPasskeysEnabled()) {
            LoginFormsProvider filled = passkeys.fillContextForm(context);
            if (filled != null) {
                form = filled;
            }
            form.setAttribute(WebAuthnConstants.ENABLE_WEBAUTHN_CONDITIONAL_UI, Boolean.TRUE);
        }
        String username = DsAuthCodes.formValue(context, "username");
        if (error != null) {
            form.setError(error);
        }
        return form.setAttribute("dsStep", "email")
                .setAttribute("dsUsername", username)
                .createForm("ds-login.ftl");
    }

    private Response credentialForm(AuthenticationFlowContext context, String error) {
        UserModel user = context.getUser();
        boolean hasPassword = user != null && user.credentialManager().isConfiguredFor(CredentialModel.PASSWORD);
        LoginFormsProvider form = context.form()
                .setAttribute("dsStep", "credential")
                .setAttribute("dsEmail", user == null ? "" : user.getEmail())
                .setAttribute("hasPassword", hasPassword);
        if (error != null) {
            form.setError(error);
        }
        return form.createForm("ds-login.ftl");
    }

    private Response codeForm(AuthenticationFlowContext context, String error) {
        UserModel user = context.getUser();
        LoginFormsProvider form = context.form()
                .setAttribute("dsStep", "code")
                .setAttribute("dsEmail", user == null ? "" : user.getEmail());
        if (error != null) {
            form.setError(error);
        }
        return form.createForm("ds-login.ftl");
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
