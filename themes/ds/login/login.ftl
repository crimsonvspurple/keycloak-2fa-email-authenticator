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
          <a class="google" id="social-${p.alias}" href="${p.loginUrl}">
            <#if p.alias == "google">
              <img src="${url.resourcesPath}/img/google.svg" alt="" width="18" height="18">
            </#if>
            Continue with ${p.displayName!}
          </a>
        </#list>
      </div>
      <#if realm.password>
        <p class="divider"><span>or</span></p>
      </#if>
    </#if>
  <#elseif section = "form">
    <#if realm.password>
      <form class="stack" action="${url.loginAction}" method="post" novalidate>
        <#if !usernameHidden??>
          <div class="field">
            <label class="micro" for="username">Email</label>
            <input id="username" name="username" type="email" autocomplete="username" autofocus
                   value="${(login.username!'')}" required>
          </div>
        </#if>
        <div class="field">
          <div class="label-row">
            <label class="micro" for="password">Password</label>
            <#if realm.resetPasswordAllowed>
              <a class="micro-link" href="${url.loginResetCredentialsUrl}">Forgot password</a>
            </#if>
          </div>
          <div class="password-row">
            <input id="password" name="password" type="password" autocomplete="current-password" required
                   <#if usernameHidden??>autofocus</#if>>
            <button class="ghost" type="button" id="password-toggle" aria-label="Show password">Show</button>
          </div>
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
        <button class="primary" id="kc-login" name="login" type="submit">Sign in</button>
      </form>
      <script>
        (function () {
          var input = document.getElementById("password");
          var button = document.getElementById("password-toggle");
          if (!input || !button) return;
          button.addEventListener("click", function () {
            var show = input.type === "password";
            input.type = show ? "text" : "password";
            button.textContent = show ? "Hide" : "Show";
            button.setAttribute("aria-label", show ? "Hide password" : "Show password");
          });
        })();
      </script>
    </#if>
  </#if>
</@layout.registrationLayout>
