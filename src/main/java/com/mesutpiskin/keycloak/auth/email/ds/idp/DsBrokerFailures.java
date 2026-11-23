package com.mesutpiskin.keycloak.auth.email.ds.idp;

import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.models.IdentityProviderModel;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.Urls;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Social broker failures leave Keycloak's login error page with HTTP 502.
 * Cloudflare replaces an origin 502 or 504 with its own Bad gateway page,
 * so the browser never sees the theme. This rewrites that response to 400
 * and renders the ds error page instead.
 */
public final class DsBrokerFailures {

    private static final Pattern BROKER_PATH = Pattern.compile(
            "(?:^|/)realms/([^/]+)/broker/([^/]+)/(endpoint|login)(?:/.*)?$");

    private DsBrokerFailures() {
    }

    public record Target(String realm, String alias) {
    }

    public static Target parse(String path) {
        if (path == null) {
            return null;
        }
        Matcher matcher = BROKER_PATH.matcher(path);
        if (!matcher.find()) {
            return null;
        }
        return new Target(matcher.group(1), matcher.group(2));
    }

    public static boolean isFailureStatus(int status) {
        return status == 400 || status == 401 || status == 403 || status == 404
                || status == 500 || status == 502 || status == 503 || status == 504;
    }

    /** Status that proxies will pass through with the page body. */
    public static int viewerStatus(int status) {
        if (status == 500 || status == 502 || status == 503 || status == 504) {
            return 400;
        }
        return status;
    }

    public static boolean replaceWithPage(MediaType type, Object entity, int status) {
        if (!isFailureStatus(status)) {
            return false;
        }
        if (type != null && MediaType.APPLICATION_JSON_TYPE.isCompatible(type)) {
            return false;
        }
        if (type != null && "html".equalsIgnoreCase(type.getSubtype())) {
            return true;
        }
        if (entity instanceof String body) {
            String trimmed = body.stripLeading();
            return trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html") || trimmed.startsWith("<HTML")
                    || trimmed.isEmpty();
        }
        return entity == null;
    }

    public static void logAndRestyle(KeycloakSession session, String path, int status, MediaType type, Object entity,
            ResponseSink sink) {
        Target target = parse(path);
        if (target == null || session == null || !isFailureStatus(status)) {
            return;
        }
        RealmModel realm = realm(session, target.realm());
        IdentityProviderModel provider = realm == null ? null : realm.getIdentityProviderByAlias(target.alias());
        Chosen chosen = choose(session, provider);
        String clientId = clientId(session);
        DsIdpFailureLog.log(target.alias(), target.realm(), clientId, chosen.stage(), chosen.status(), chosen.snippet());
        if (!replaceWithPage(type, entity, status)) {
            return;
        }
        String name = DsIdpSnippets.displayName(target.alias(), provider == null ? null : provider.getDisplayName());
        String html = render(session, name);
        sink.replace(viewerStatus(status), html);
    }

    private static RealmModel realm(KeycloakSession session, String name) {
        RealmModel current = session.getContext().getRealm();
        if (current != null && name.equals(current.getName())) {
            return current;
        }
        return session.realms().getRealmByName(name);
    }

    private static String clientId(KeycloakSession session) {
        AuthenticationSessionModel auth = session.getContext().getAuthenticationSession();
        if (auth != null && auth.getClient() != null) {
            return auth.getClient().getClientId();
        }
        if (session.getContext().getClient() != null) {
            return session.getContext().getClient().getClientId();
        }
        return "-";
    }

    private static Chosen choose(KeycloakSession session, IdentityProviderModel provider) {
        List<DsIdpCall> calls = DsIdpCall.recorded(session);
        if (calls.isEmpty()) {
            return new Chosen(DsIdpStages.OTHER, 0, "");
        }
        String tokenUrl = provider == null || provider.getConfig() == null ? null : provider.getConfig().get("tokenUrl");
        String userInfoUrl = provider == null || provider.getConfig() == null ? null : provider.getConfig().get("userInfoUrl");
        DsIdpCall best = calls.get(calls.size() - 1);
        String stage = DsIdpStages.stage(best.url(), tokenUrl, userInfoUrl);
        for (int i = calls.size() - 1; i >= 0; i--) {
            DsIdpCall call = calls.get(i);
            String candidate = DsIdpStages.stage(call.url(), tokenUrl, userInfoUrl);
            if (!DsIdpStages.OTHER.equals(candidate)) {
                best = call;
                stage = candidate;
                break;
            }
        }
        return new Chosen(stage, best.status(), DsIdpSnippets.snippet(best.body()));
    }

    private static String render(KeycloakSession session, String providerName) {
        try {
            AuthenticationSessionModel auth = session.getContext().getAuthenticationSession();
            if (auth != null && session.getContext().getClient() == null && auth.getClient() != null) {
                session.getContext().setClient(auth.getClient());
            }
            LoginFormsProvider forms = session.getProvider(LoginFormsProvider.class);
            if (auth != null) {
                forms.setAuthenticationSession(auth);
            }
            forms.setAttribute("dsIdpFailure", Boolean.TRUE);
            if (providerName != null && !providerName.isBlank()) {
                forms.setAttribute("dsIdpDisplayName", providerName);
            }
            forms.setError("identityProviderUnexpectedErrorMessage");
            Response page = forms.createErrorPage(Response.Status.BAD_REQUEST);
            if (page.getEntity() instanceof String html && html.contains("ds-idp-failure")) {
                return html;
            }
        } catch (RuntimeException ex) {
            // The fallback below still returns a page. The theme error is already logged by Keycloak.
        }
        return fallback(session, providerName);
    }

    static String fallback(KeycloakSession session, String providerName) {
        String restart = "#";
        try {
            RealmModel realm = session.getContext().getRealm();
            URI base = session.getContext().getUri().getBaseUri();
            if (realm != null && base != null) {
                restart = Urls.realmLoginRestartPage(base, realm.getName(), false).toString();
            }
        } catch (RuntimeException ex) {
            restart = "#";
        }
        String sentence = DsIdpSnippets.html(DsIdpSnippets.sentence(providerName));
        String href = DsIdpSnippets.html(restart);
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Couldn't sign you in</title></head><body><main>"
                + "<h1>Couldn't sign you in</h1><p id=\"ds-idp-failure\">" + sentence + "</p>"
                + "<p><a id=\"loginRestartLink\" href=\"" + href + "\">Back to sign in</a></p>"
                + "</main></body></html>";
    }

    public interface ResponseSink {
        void replace(int status, String html);
    }

    private record Chosen(String stage, int status, String snippet) {
    }

}
