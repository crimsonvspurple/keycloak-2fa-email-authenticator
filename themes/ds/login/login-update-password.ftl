<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('password','password-confirm'); section>
  <#if section = "header">
    Set a password
  <#elseif section = "summary">
    <p class="sub">Choose a password for this DobberSports account.</p>
  <#elseif section = "form">
    <form class="stack" id="kc-passwd-update-form" action="${url.loginAction}" method="post">
      <div class="field">
        <label class="micro" for="password-new">New password</label>
        <input id="password-new" name="password-new" type="password" autocomplete="new-password" autofocus required>
      </div>
      <div class="field">
        <label class="micro" for="password-confirm">Confirm password</label>
        <input id="password-confirm" name="password-confirm" type="password" autocomplete="new-password" required>
      </div>
      <label class="check">
        <input id="logout-sessions" name="logout-sessions" type="checkbox" value="on" checked>
        Sign out other sessions
      </label>
      <#if messagesPerField.existsError('password','password-confirm')>
        <p class="field-error" role="alert">${kcSanitize(messagesPerField.getFirstError('password','password-confirm'))?no_esc}</p>
      </#if>
      <button class="primary" id="kc-submit" name="login" type="submit">Save password</button>
      <#if isAppInitiatedAction??>
        <button class="text-button" id="kc-cancel" name="cancel-aia" type="submit">Cancel</button>
      </#if>
    </form>
  </#if>
</@layout.registrationLayout>
