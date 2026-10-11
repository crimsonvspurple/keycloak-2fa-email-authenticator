<#import "template.ftl" as layout>
<#if enableWebAuthnConditionalUI?has_content>
  <#import "passkeys.ftl" as passkeys>
</#if>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    <#if dsStep! == "code">
      Enter your code
    <#elseif dsStep! == "credential">
      Sign in
    <#else>
      Sign in<#if (client.name!"")?has_content> to ${(client.name!"")?remove_ending(" web")}</#if>
    </#if>
  <#elseif section = "summary">
    <#if dsStep! == "code">
      <p class="sub">We sent a code and a link for <span class="attempted">${dsEmail!}</span>. The link works in this browser. The code confirms this email.</p>
    <#elseif dsStep! == "credential">
      <p class="sub"><span class="attempted">${dsEmail!}</span></p>
      <#if !hasPassword!false>
        <p class="sub">This account has no password. A code signs you in, and you can set a password on that page.</p>
      </#if>
    <#else>
      <p class="sub">Enter your email. A new address gets an account and a sign-in code. Google does the same.</p>
    </#if>
  <#elseif section = "socialProviders">
    <#if dsStep! == "email" && social.providers?? && social.providers?has_content>
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
    </#if>
  <#elseif section = "form">
    <#if dsStep! == "email" && enableWebAuthnConditionalUI?has_content>
      <@passkeys.conditionalUIData />
      <p class="passkey-note">No passkey yet? Sign in, then turn on Create a passkey.</p>
    </#if>
    <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
      <p class="alert alert-${message.type}" role="${(message.type = 'error')?then('alert','status')}">
        ${kcSanitize(message.summary)?no_esc}
      </p>
    </#if>

    <#if dsStep! == "code">
      <form class="stack" id="kc-form-login" action="${url.loginAction}" method="post" novalidate>
        <@layout.dsOtpField />
        <button class="text-button" type="button" id="show-set-password">Set a password</button>
        <div id="set-password" hidden>
          <div class="field">
            <label class="micro" for="password-new">New password</label>
            <input id="password-new" name="password-new" type="password" autocomplete="new-password">
          </div>
          <div class="field">
            <label class="micro" for="password-confirm">Confirm password</label>
            <input id="password-confirm" name="password-confirm" type="password" autocomplete="new-password">
          </div>
        </div>
        <@layout.dsPasskeySwitch />
        <button class="primary" name="login" type="submit">Continue</button>
        <button class="secondary" name="resend" type="submit" formnovalidate>Email me a new code</button>
        <button class="text-button" name="restart" type="submit" formnovalidate>Use a different email</button>
      </form>
    <#elseif dsStep! == "credential">
      <form class="stack" id="kc-form-login" action="${url.loginAction}" method="post" novalidate>
        <#if hasPassword!false>
        <div class="field">
          <label class="micro" for="password">Password</label>
          <div class="password-row">
            <input id="password" name="password" type="password" autocomplete="current-password" autofocus required>
            <button class="ghost" type="button" id="password-toggle" aria-label="Show password">Show</button>
          </div>
        </div>
        </#if>
        <#if hasPassword!false>
          <button class="primary" id="kc-login" name="login" type="submit">Sign in</button>
          <button class="secondary" name="email-code" type="submit" formnovalidate>Email me a code</button>
          <#if realm.resetPasswordAllowed>
            <a class="text-button" href="${url.loginResetCredentialsUrl}">Forgot password</a>
          </#if>
        <#else>
          <button class="primary" name="email-code" type="submit">Email me a code</button>
        </#if>
        <@layout.dsPasskeySwitch />
        <button class="text-button" name="restart" type="submit" formnovalidate>Use a different email</button>
      </form>
    <#else>
      <form class="stack" id="kc-form-login" action="${url.loginAction}" method="post" novalidate>
        <#if (social.providers?? && social.providers?has_content) || enableWebAuthnConditionalUI?has_content>
          <p class="divider"><span>or</span></p>
        </#if>
        <div class="field">
          <label class="micro" for="username">Email</label>
          <#assign attempted = dsUsername!''>
          <#if !attempted?has_content && login??>
            <#assign attempted = login.username!''>
          </#if>
          <input id="username" name="username" type="email" autocomplete="${(enableWebAuthnConditionalUI?has_content)?then('username webauthn', 'username')}"
                 value="${attempted}" required>
        </div>
        <button class="primary" name="continue" type="submit">Continue</button>
        <#if realm.resetPasswordAllowed>
          <a class="text-button" href="${url.loginResetCredentialsUrl}">Forgot password</a>
        </#if>
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
          if (!valid(stored)) stored = readCookie();
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
        var email = document.getElementById("username");
        var focusedLast = false;
        if (social && current && current !== "password") {
          var last = document.getElementById("social-" + current);
          if (last && social.contains(last)) {
            last.classList.add("last-used");
            var tag = document.createElement("span");
            tag.className = "last-used-tag";
            tag.textContent = "Last used";
            last.appendChild(tag);
            if (last !== social.firstElementChild) social.insertBefore(last, social.firstElementChild);
            last.focus({ preventScroll: true });
            focusedLast = true;
          }
        }
        if (!focusedLast && email) email.focus({ preventScroll: true });
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
        var signIn = document.getElementById("kc-login");
        if (signIn) signIn.addEventListener("click", function () { write("password"); });
        var showPassword = document.getElementById("show-set-password");
        var setPassword = document.getElementById("set-password");
        if (showPassword && setPassword) {
          showPassword.addEventListener("click", function () {
            setPassword.hidden = false;
            showPassword.hidden = true;
            var field = document.getElementById("password-new");
            if (field) field.focus();
          });
        }
      })();
    </script>
  </#if>
</@layout.registrationLayout>
