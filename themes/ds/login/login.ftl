<#import "template.ftl" as layout>
<#function productName>
  <#assign raw = (client.name!"")?trim>
  <#if raw?ends_with(" web")>
    <#return raw?substring(0, raw?length - 4)>
  </#if>
  <#return raw>
</#function>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password'); section>
  <#if section = "header">
    Sign in<#if productName()?has_content> to ${productName()}</#if>
  <#elseif section = "summary">
    <p class="sub">Use your DobberSports account.</p>
  <#elseif section = "socialProviders">
    <#if social.providers?? && social.providers?has_content>
      <div class="social">
        <#list social.providers as p>
          <a class="social-btn" id="social-${p.alias}" data-alias="${p.alias}" href="${p.loginUrl}">
            <#if p.alias == "google">
              <img src="${url.resourcesPath}/img/google.svg?v=${properties.dsVersion}" alt="" width="18" height="18">
            </#if>
            <span class="social-label">Continue with ${p.displayName!}</span>
          </a>
        </#list>
      </div>
      <#if realm.password>
        <p class="divider"><span>or</span></p>
      </#if>
    </#if>
  <#elseif section = "form">
    <#if realm.password>
      <form class="stack login-grid" id="kc-form-login" action="${url.loginAction}" method="post" novalidate>
        <#if !usernameHidden??>
          <div class="field email-field">
            <label class="micro" for="username">Email</label>
            <input id="username" name="username" type="email" autocomplete="username" autofocus
                   value="${(login.username!'')}" required>
          </div>
        </#if>
        <label class="micro pw-label" for="password">Password</label>
        <div class="password-row pw-input">
          <input id="password" name="password" type="password" autocomplete="current-password" required
                 <#if usernameHidden??>autofocus</#if>>
        </div>
        <#if realm.rememberMe && !usernameHidden??>
          <label class="check">
            <input name="rememberMe" type="checkbox" <#if login.rememberMe??>checked</#if>>
            Remember me
          </label>
        </#if>
        <input type="hidden" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>>
        <#if messagesPerField.existsError('username','password')>
          <p class="field-error" role="alert">${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}</p>
        </#if>
        <button class="primary pw-submit" id="kc-login" name="login" type="submit">Sign in</button>
        <#if realm.resetPasswordAllowed>
          <a class="micro-link pw-forgot" href="${url.loginResetCredentialsUrl}">Forgot password</a>
        </#if>
        <button class="ghost pw-toggle" type="button" id="password-toggle" aria-label="Show password">Show</button>
      </form>
    </#if>
    <script>
      (function () {
        var input = document.getElementById("password");
        var toggle = document.getElementById("password-toggle");
        if (input && toggle) {
          toggle.addEventListener("click", function () {
            var show = input.type === "password";
            input.type = show ? "text" : "password";
            toggle.textContent = show ? "Hide" : "Show";
            toggle.setAttribute("aria-label", show ? "Hide password" : "Show password");
          });
        }

        var KEY = "ds-last-idp";
        var MAX_AGE = 60 * 60 * 24 * 400;
        function valid(value) {
          return value === "password" || /^[A-Za-z0-9][A-Za-z0-9._-]{0,40}$/.test(value);
        }
        function readCookie() {
          var parts = document.cookie ? document.cookie.split("; ") : [];
          for (var i = 0; i < parts.length; i++) {
            if (parts[i].indexOf(KEY + "=") === 0) {
              try { return decodeURIComponent(parts[i].slice(KEY.length + 1)); }
              catch (e) { return ""; }
            }
          }
          return "";
        }
        function read() {
          var stored = "";
          try { stored = localStorage.getItem(KEY) || ""; } catch (e) {}
          if (!valid(stored)) {
            stored = readCookie();
          }
          return valid(stored) ? stored : "";
        }
        function write(value) {
          if (!valid(value)) return;
          try { localStorage.setItem(KEY, value); } catch (e) {}
          document.cookie = KEY + "=" + encodeURIComponent(value)
            + "; Max-Age=" + MAX_AGE + "; Path=/; Secure; SameSite=Lax";
        }
        function aliasOf(node) {
          var link = node && node.closest ? node.closest("a[data-alias]") : null;
          return link ? (link.getAttribute("data-alias") || "") : "";
        }

        var current = read();
        if (current) write(current);

        var social = document.querySelector(".social");
        if (social && current && current !== "password") {
          var last = document.getElementById("social-" + current);
          if (last && social.contains(last)) {
            last.classList.add("last-used");
            var tag = document.createElement("span");
            tag.className = "last-used-tag";
            tag.textContent = "Last used";
            last.appendChild(tag);
            if (last !== social.firstElementChild) social.insertBefore(last, social.firstElementChild);
          }
        }
        function rememberSocial(event) {
          var alias = aliasOf(event.target);
          if (valid(alias) && alias !== "password") write(alias);
        }
        if (social) {
          social.addEventListener("click", rememberSocial);
          social.addEventListener("auxclick", function (event) {
            if (event.button === 1) rememberSocial(event);
          });
        }
        var form = document.getElementById("kc-form-login");
        if (form) form.addEventListener("submit", function () { write("password"); });
      })();
    </script>
  </#if>
</@layout.registrationLayout>
