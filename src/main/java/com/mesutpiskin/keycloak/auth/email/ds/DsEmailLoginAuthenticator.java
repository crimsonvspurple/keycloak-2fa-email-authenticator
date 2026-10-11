package com.mesutpiskin.keycloak.auth.email.ds;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.keycloak.WebAuthnConstants;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.WebAuthnConditionalUIAuthenticator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.policy.PasswordPolicyManagerProvider;
import org.keycloak.policy.PolicyError;
import org.keycloak.userprofile.UserProfile;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.userprofile.UserProfileProvider;

/**
 * Email first. A new email creates an account and sends a code. A password is optional.
 * Google still creates, links, or signs in on its own. A code or link marks the email verified.
 */
public class DsEmailLoginAuthenticator implements Authenticator {

    static final String STEP = "ds.step";
    static final String PASSKEY_ACTION = DsPasskeyRegisterFactory.PROVIDER_ID;

    private static final Logger LOG = Logger.getLogger(DsEmailLoginAuthenticator.class);

    private final WebAuthnConditionalUIAuthenticator passkeys;

    public DsEmailLoginAuthenticator(KeycloakSession session) {
        this.passkeys = new WebAuthnConditionalUIAuthenticator(session, this::emailForm);
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        if (acceptMagic(context)) {
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
        if (acceptMagic(context)) {
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
                finishCode(context);
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

    private boolean acceptMagic(AuthenticationFlowContext context) {
        if (!DsAuthCodes.hasMagic(context)) {
            return false;
        }
        if (!DsAuthCodes.magicMatches(context)) {
            context.challenge(codeForm(context, "That link is no longer valid. Enter the code instead."));
            return true;
        }
        markEmailVerified(context);
        offerPasskey(context);
        context.success();
        return true;
    }

    private void lookup(AuthenticationFlowContext context, String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!email.contains("@") || email.length() > 320 || email.startsWith("@") || email.endsWith("@")) {
            context.challenge(emailForm(context, "Enter the email on the account."));
            return;
        }
        RealmModel realm = context.getRealm();
        UserModel user = KeycloakModelUtils.findUserByNameOrEmail(context.getSession(), realm, email);
        boolean created = false;
        if (user == null) {
            user = createUser(context, email);
            if (user == null) {
                context.challenge(emailForm(context, "That email could not be registered. Try again."));
                return;
            }
            created = true;
        }
        if (!user.isEnabled()) {
            context.getEvent().error(Errors.USER_DISABLED);
            context.challenge(emailForm(context, "That account is disabled."));
            return;
        }
        context.setUser(user);
        if (created) {
            sendCode(context);
            return;
        }
        context.getAuthenticationSession().setAuthNote(STEP, "credential");
        context.challenge(credentialForm(context, null));
    }

    private UserModel createUser(AuthenticationFlowContext context, String email) {
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();
        try {
            UserProfileProvider profiles = session.getProvider(UserProfileProvider.class);
            if (profiles != null) {
                Map<String, List<String>> attributes = new HashMap<>();
                attributes.put(UserModel.USERNAME, List.of(email));
                attributes.put(UserModel.EMAIL, List.of(email));
                UserProfile profile = profiles.create(UserProfileContext.REGISTRATION, attributes);
                profile.validate();
                UserModel user = profile.create();
                user.setEnabled(true);
                user.setEmailVerified(false);
                return user;
            }
        } catch (ModelDuplicateException ex) {
            return KeycloakModelUtils.findUserByNameOrEmail(session, realm, email);
        } catch (RuntimeException ex) {
            LOG.warn("Profile create failed, trying a plain user");
        }
        try {
            UserModel user = session.users().addUser(realm, email);
            user.setEmail(email);
            user.setEnabled(true);
            user.setEmailVerified(false);
            return user;
        } catch (ModelDuplicateException ex) {
            return KeycloakModelUtils.findUserByNameOrEmail(session, realm, email);
        } catch (RuntimeException ex) {
            LOG.warn("User create failed");
            return null;
        }
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
        offerPasskey(context);
        context.success();
    }

    private void finishCode(AuthenticationFlowContext context) {
        String passwordError = storeOptionalPassword(context);
        if (passwordError != null) {
            context.challenge(codeForm(context, passwordError));
            return;
        }
        markEmailVerified(context);
        offerPasskey(context);
        DsAuthCodes.clear(context);
        context.success();
    }

    private String storeOptionalPassword(AuthenticationFlowContext context) {
        String password = DsAuthCodes.formValue(context, "password-new");
        String confirm = DsAuthCodes.formValue(context, "password-confirm");
        if (password.isBlank() && confirm.isBlank()) {
            return null;
        }
        if (!password.equals(confirm)) {
            return "Those passwords do not match.";
        }
        UserModel user = context.getUser();
        if (user == null) {
            return "Start again and enter the email.";
        }
        PasswordPolicyManagerProvider policy = context.getSession().getProvider(PasswordPolicyManagerProvider.class);
        if (policy != null) {
            PolicyError error = policy.validate(context.getRealm(), user, password);
            if (error != null) {
                return "That password does not meet the rules. Try a longer one.";
            }
        }
        user.credentialManager().updateCredential(UserCredentialModel.password(password));
        return null;
    }

    private void markEmailVerified(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user != null && !user.isEmailVerified()) {
            user.setEmailVerified(true);
        }
    }

    private void offerPasskey(AuthenticationFlowContext context) {
        if (DsAuthCodes.pressed(context, "create-passkey")) {
            context.getAuthenticationSession().addRequiredAction(PASSKEY_ACTION);
        }
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
            context.getAuthenticationSession().setAuthNote(STEP, "credential");
            context.challenge(credentialForm(context, "The code could not be sent. Try again in a moment."));
            return;
        }
        context.getAuthenticationSession().setAuthNote(STEP, "code");
        context.challenge(codeForm(context, null));
    }

    private void restart(AuthenticationFlowContext context) {
        context.clearUser();
        context.getAuthenticationSession().removeAuthNote(STEP);
        DsAuthCodes.clear(context);
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
        // A password and a passkey are optional. The code page offers both.
    }

    @Override
    public void close() {
    }
}
