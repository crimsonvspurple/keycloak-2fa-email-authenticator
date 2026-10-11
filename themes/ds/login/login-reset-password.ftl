<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true displayMessage=!messagesPerField.existsError('username'); section>
  <#if section = "header">
    Reset password
  <#elseif section = "summary">
    <p class="sub">We will send reset instructions if an account uses this email.</p>
  <#elseif section = "form">
    <form class="stack" action="${url.loginAction}" method="post">
      <div class="field">
        <label class="micro" for="username">Email</label>
        <input id="username" name="username" type="email" autocomplete="username" autofocus required
               value="${(auth.attemptedUsername!'')}">
      </div>
      <#if messagesPerField.existsError('username')>
        <p class="field-error" role="alert">${kcSanitize(messagesPerField.get('username'))?no_esc}</p>
      </#if>
      <button class="primary" type="submit">Send reset link</button>
      <a class="text-button" href="${url.loginUrl}">Back to sign in</a>
    </form>
  <#elseif section = "info">
    <p class="sub">The message goes to the address on the account.</p>
  </#if>
</@layout.registrationLayout>
