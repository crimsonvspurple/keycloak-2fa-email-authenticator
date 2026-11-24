package com.mesutpiskin.keycloak.auth.email.ds.idp;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import freemarker.core.HTMLOutputFormat;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateMethodModelEx;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DsIdpFailureTest {

    @Test
    void suspendedAccountSnippetKeepsTheXTitleAndDropsSecrets() {
        String body = """
                {"title":"Forbidden","detail":"User has been suspended","type":"about:blank","status":403,\
                "access_token":"ya29.secret-token","refresh_token":"1//refresh-secret","code":"auth-code-value"}
                """;

        String snippet = DsIdpSnippets.snippet(body);

        assertTrue(snippet.contains("title=Forbidden"));
        assertTrue(snippet.contains("type=about:blank"));
        assertTrue(snippet.contains("status=403"));
        assertTrue(snippet.contains("detail=User has been suspended"));
        assertFalse(snippet.contains("ya29"));
        assertFalse(snippet.contains("refresh-secret"));
        assertFalse(snippet.contains("auth-code-value"));
        assertTrue(snippet.length() <= DsIdpSnippets.MAX_CHARS);
    }

    @Test
    void tokenErrorSnippetRedactsTokensEmailsAndBearerValues() {
        String body = """
                {"error":"invalid_grant","error_description":"rejected for user@example.com bearer ya.token \
                access_token=ya29.another"}
                """;

        String snippet = DsIdpSnippets.snippet(body);

        assertTrue(snippet.contains("error=invalid_grant"));
        assertTrue(snippet.contains("[email]"));
        assertFalse(snippet.contains("user@example.com"));
        assertFalse(snippet.contains("ya29.another"));
        assertFalse(snippet.contains("ya.token"));
    }

    @Test
    void classifiesTokenAndProfileUrlsWithoutKeepingTheQuery() {
        assertEquals("token exchange", DsIdpStages.stage(
                "https://api.twitter.com/2/oauth2/token?code=secret",
                "https://api.twitter.com/2/oauth2/token",
                "https://api.twitter.com/2/users/me"));
        assertEquals("profile fetch", DsIdpStages.stage(
                "https://api.twitter.com/2/users/me?user.fields=email",
                null,
                null));
        assertEquals("profile fetch", DsIdpStages.stage(
                "https://openidconnect.googleapis.com/v1/userinfo",
                "https://oauth2.googleapis.com/token",
                "https://openidconnect.googleapis.com/v1/userinfo"));
        assertEquals("other", DsIdpStages.stage("https://www.googleapis.com/oauth2/v3/certs", null, null));
        assertEquals("https://api.twitter.com/2/oauth2/token",
                DsIdpStages.withoutSecrets("https://user:pass@api.twitter.com/2/oauth2/token?code=secret#frag"));
    }

    @Test
    void brokerFailureBecomesANormalPageStatus() {
        assertEquals("x", DsBrokerFailures.parse("/realms/ds/broker/x/endpoint").alias());
        assertEquals("ds", DsBrokerFailures.parse("/realms/ds/broker/google/login").realm());
        assertNull(DsBrokerFailures.parse("/realms/ds/protocol/openid-connect/auth"));
        assertEquals(400, DsBrokerFailures.viewerStatus(502));
        assertEquals(400, DsBrokerFailures.viewerStatus(504));
        assertEquals(400, DsBrokerFailures.viewerStatus(500));
        assertEquals(400, DsBrokerFailures.viewerStatus(400));
        assertTrue(DsBrokerFailures.replaceWithPage(MediaType.TEXT_HTML_TYPE, "<html></html>", 502));
        assertTrue(DsBrokerFailures.replaceWithPage(null, null, 502));
        assertFalse(DsBrokerFailures.replaceWithPage(MediaType.APPLICATION_JSON_TYPE, "{}", 502));
        assertFalse(DsBrokerFailures.isFailureStatus(302));
    }

    @Test
    void logLineNamesTheProviderAndOmitsTheUpstreamBody() {
        String line = DsIdpFailureLog.line("x", "ds", "gp-web", "profile fetch", 403,
                DsIdpSnippets.snippet("{\"title\":\"Forbidden\",\"access_token\":\"ya29.SECRET\"}"));

        assertTrue(line.contains("provider=x"));
        assertTrue(line.contains("realm=ds"));
        assertTrue(line.contains("client_id=gp-web"));
        assertTrue(line.contains("stage=profile fetch"));
        assertTrue(line.contains("upstream_status=403"));
        assertTrue(line.contains("title=Forbidden"));
        assertFalse(line.contains("ya29"));
        assertFalse(line.contains("\n"));
    }

    @Test
    void serverErrorFileSkipsOrdinaryUserMistakes() {
        assertFalse(DsServerErrorEvents.include("LOGIN_ERROR", "invalid_user_credentials"));
        assertFalse(DsServerErrorEvents.include("LOGIN_ERROR", "expired_code"));
        assertFalse(DsServerErrorEvents.include("LOGIN_ERROR", "invalid_code"));
        assertFalse(DsServerErrorEvents.include("CODE_TO_TOKEN_ERROR", "invalid_token"));
        assertFalse(DsServerErrorEvents.include("LOGIN", null));
        assertTrue(DsServerErrorEvents.include("IDENTITY_PROVIDER_LOGIN_ERROR", "identity_provider_login_failure"));
        assertTrue(DsServerErrorEvents.include("LOGIN_ERROR", "email_send_failed"));
        assertTrue(DsServerErrorEvents.include("CODE_TO_TOKEN_ERROR", "invalid_client"));
        assertFalse(DsServerErrorEvents.include("IDENTITY_PROVIDER_LOGIN_ERROR", "access_denied"));

        String line = DsServerErrorEvents.line(
                "IDENTITY_PROVIDER_LOGIN_ERROR", "ds", "gp-web", "identity_provider_login_failure", "x",
                "user@example.com");
        assertTrue(line.contains("type=IDENTITY_PROVIDER_LOGIN_ERROR"));
        assertTrue(line.contains("provider=x"));
        assertTrue(line.contains("user_id=-"));
        assertFalse(line.contains("user@example.com"));
    }

    @Test
    void friendlySentenceNamesTheProviderAndEscapesIt() {
        assertEquals(
                "We couldn't sign you in with X. Your X account may be suspended or restricted, "
                        + "or X is temporarily unavailable. Please try another sign-in method.",
                DsIdpSnippets.sentence("X"));
        assertFalse(DsIdpSnippets.sentence("").contains("Your  account"));
        assertEquals("X", DsIdpSnippets.displayName("x", null));
        assertEquals("Custom", DsIdpSnippets.displayName("x", "Custom"));
    }

    @Test
    void capturedBodyIsWhatTheSnippetReads() {
        DsIdpCall call = new DsIdpCall("https://api.twitter.com/2/users/me", 403, StandardCharsets.UTF_8);
        byte[] bytes = "{\"title\":\"Forbidden\"}".getBytes(StandardCharsets.UTF_8);
        call.offer(bytes, 0, bytes.length);
        assertEquals("title=Forbidden", DsIdpSnippets.snippet(call.body()));
        assertEquals(403, call.status());
    }

    @Test
    void badGatewayIsRewrittenToTheFriendlyPage() {
        KeycloakSession session = mock(KeycloakSession.class);
        KeycloakContext context = mock(KeycloakContext.class);
        RealmModel realm = mock(RealmModel.class);
        IdentityProviderModel provider = mock(IdentityProviderModel.class);
        AuthenticationSessionModel auth = mock(AuthenticationSessionModel.class);
        ClientModel client = mock(ClientModel.class);
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);
        when(realm.getName()).thenReturn("ds");
        when(realm.getIdentityProviderByAlias("x")).thenReturn(provider);
        when(provider.getDisplayName()).thenReturn("X");
        when(provider.getConfig()).thenReturn(Map.of("userInfoUrl", "https://api.twitter.com/2/users/me"));
        when(context.getAuthenticationSession()).thenReturn(auth);
        when(context.getClient()).thenReturn(null);
        when(auth.getClient()).thenReturn(client);
        when(client.getClientId()).thenReturn("gp-web");
        byte[] body = "{\"title\":\"Forbidden\",\"detail\":\"User has been suspended\"}".getBytes(StandardCharsets.UTF_8);
        DsIdpCall call = new DsIdpCall("https://api.twitter.com/2/users/me", 403, StandardCharsets.UTF_8);
        call.offer(body, 0, body.length);
        when(session.getAttribute("ds.idp.upstream")).thenReturn(List.of(call));
        when(session.getProvider(LoginFormsProvider.class)).thenThrow(new IllegalStateException("no renderer"));
        when(context.getUri()).thenThrow(new IllegalStateException("no uri"));

        int[] status = {0};
        String[] html = {""};
        DsBrokerFailures.logAndRestyle(session, "/realms/ds/broker/x/endpoint", 502,
                MediaType.TEXT_HTML_TYPE, "<html></html>", (next, page) -> {
                    status[0] = next;
                    html[0] = page;
                });

        assertEquals(400, status[0]);
        assertTrue(html[0].contains("id=\"ds-idp-failure\""));
        assertTrue(html[0].contains("We couldn't sign you in with X."));
        assertTrue(html[0].contains("id=\"loginRestartLink\""));
        assertFalse(html[0].contains("User has been suspended"));
    }

    @Test
    void errorPageRendersWhenTheMessageAndClientAreMissing() throws Exception {
        String html = render("error.ftl", Map.of());
        assertTrue(html.contains("Something went wrong"));
        assertTrue(html.contains("id=\"loginRestartLink\""));
        assertTrue(html.contains("/realms/ds/login-actions/restart"));
        assertFalse(html.contains("id=\"ds-idp-failure\""));
    }

    @Test
    void errorPageNamesXAndOffersRestart() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("dsIdpFailure", Boolean.TRUE);
        model.put("dsIdpDisplayName", "X");
        String html = render("error.ftl", model);
        assertTrue(html.contains("id=\"ds-idp-failure\""));
        assertTrue(html.contains("We couldn't sign you in with X."));
        assertTrue(html.contains("Your X account may be suspended or restricted"));
        assertTrue(html.contains("id=\"loginRestartLink\""));
        assertFalse(html.contains("Exception"));
    }

    @Test
    void providerNameIsEscaped() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("dsIdpFailure", Boolean.TRUE);
        model.put("dsIdpDisplayName", "A<B");
        String html = render("error.ftl", model);
        assertTrue(html.contains("A&lt;B"));
        assertFalse(html.contains("A<B"));
    }

    @Test
    void infoAndLoginTemplatesSurviveASparseErrorModel() throws Exception {
        render("info.ftl", Map.of());
        render("login.ftl", Map.of());
        render("ds-login.ftl", Map.of());
        render("ds-reset-code.ftl", Map.of());
    }

    @Test
    void appleSocialButtonUsesTheLogoGlyph() throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("dsStep", "email");
        model.put("social", Map.of("providers", List.of(
                provider("google", "Google"),
                provider("x", "X"),
                provider("linkedin", "LinkedIn"),
                provider("facebook", "Facebook"),
                provider("apple", "Apple"))));

        for (String template : List.of("login.ftl", "ds-login.ftl")) {
            String html = render(template, model);
            int apple = html.indexOf("id=\"social-apple\"");
            int close = html.indexOf("</a>", apple);
            assertTrue(apple >= 0, template);
            String button = html.substring(apple, close);
            assertTrue(button.contains("data-alias=\"apple\""), template);
            assertTrue(button.contains("class=\"social-mark\""), template);
            assertTrue(button.contains("fill=\"currentColor\""), template);
            assertTrue(button.contains("M12.152 6.896"), template);
            assertTrue(button.contains(">Apple</span>"), template);
            assertTrue(html.contains("/img/google.svg?v=test"), template);
            assertTrue(html.contains("/img/linkedin.svg?v=test"), template);
            assertTrue(html.contains("/img/facebook.svg?v=test"), template);
            assertTrue(html.contains("M18.244 2.25h3.308"), template);
            assertTrue(html.contains(">Google</span>"), template);
            assertTrue(html.contains(">X</span>"), template);
            assertTrue(html.contains(">LinkedIn</span>"), template);
            assertTrue(html.contains(">Facebook</span>"), template);
        }
    }

    private static Map<String, String> provider(String alias, String displayName) {
        return Map.of("alias", alias, "displayName", displayName, "loginUrl", "/broker/" + alias);
    }

    private static String render(String templateName, Map<String, Object> extra) throws Exception {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
        configuration.setDirectoryForTemplateLoading(new File("themes/ds/login"));
        configuration.setDefaultEncoding("UTF-8");
        configuration.setOutputFormat(HTMLOutputFormat.INSTANCE);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setSharedVariable("kcSanitize", (TemplateMethodModelEx) args -> String.valueOf(args.get(0)));
        configuration.setSharedVariable("msg", (TemplateMethodModelEx) args -> args.isEmpty() ? "" : String.valueOf(args.get(0)));
        Map<String, Object> model = new HashMap<>();
        model.put("lang", "en");
        model.put("url", new UrlStub());
        Properties properties = new Properties();
        properties.setProperty("dsVersion", "test");
        model.put("properties", properties);
        model.putAll(extra);
        Template template = configuration.getTemplate(templateName);
        StringWriter writer = new StringWriter();
        template.process(model, writer);
        return writer.toString();
    }

    public static final class UrlStub {
        public String getResourcesPath() {
            return "/resources";
        }

        public String getResourcesCommonPath() {
            return "/resources/common";
        }

        public String getLoginRestartFlowUrl() {
            return "/realms/ds/login-actions/restart";
        }

        public String getLoginAction() {
            return "/login-action";
        }

        public String getLoginUrl() {
            return "/login";
        }

        public String getLoginResetCredentialsUrl() {
            return "/reset";
        }

        public String getRegistrationUrl() {
            return "/register";
        }
    }
}
