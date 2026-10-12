<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('email','password','password-confirm') displayInfo=true pageTitle="Create account"; section>
  <#if section = "header">
    Create your account
  <#elseif section = "summary">
    <p class="sub">One DobberSports account works on Goaliepost, Dobbernomics, and Geek.</p>
  <#elseif section = "form">
    <form class="stack" id="kc-register-form" action="${url.registrationAction}" method="post">
      <div class="field">
        <label class="micro" for="email">Email</label>
        <input id="email" name="email" type="email" autocomplete="email" autofocus required
               value="${(register.formData.email!'')}">
      </div>
      <#if messagesPerField.existsError('email')>
        <p class="field-error" role="alert">${kcSanitize(messagesPerField.get('email'))?no_esc}</p>
      </#if>
      <div class="field">
        <label class="micro" for="password">Password</label>
        <input id="password" name="password" type="password" autocomplete="new-password" required>
      </div>
      <div class="field">
        <label class="micro" for="password-confirm">Confirm password</label>
        <input id="password-confirm" name="password-confirm" type="password" autocomplete="new-password" required>
      </div>
      <#if messagesPerField.existsError('password','password-confirm')>
        <p class="field-error" role="alert">${kcSanitize(messagesPerField.getFirstError('password','password-confirm'))?no_esc}</p>
      </#if>
      <@layout.dsTurnstile "register" />
      <button class="primary" id="kc-register" name="register" type="submit">Create account</button>
    </form>
  <#elseif section = "info">
    <p class="sub"><a href="${url.loginUrl}">Back to sign in</a></p>
  </#if>
</@layout.registrationLayout>
