<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    Enter your code
  <#elseif section = "summary">
    <p class="sub">We sent a code and a link<#if dsEmail?has_content> for <span class="attempted">${dsEmail}</span></#if>. Enter the code, or open the link in this browser. Then choose a password.</p>
  <#elseif section = "form">
    <#if message?has_content>
      <p class="alert alert-${message.type}" role="${(message.type = 'error')?then('alert','status')}">
        ${kcSanitize(message.summary)?no_esc}
      </p>
    </#if>
    <form class="stack" action="${url.loginAction}" method="post" novalidate>
      <@layout.dsOtpField />
      <@layout.dsTurnstile "otp" />
      <button class="primary" name="login" type="submit">Continue</button>
      <@layout.dsResendButton />
      <a class="text-button" href="${url.loginUrl}">Back to sign in</a>
    </form>
  </#if>
</@layout.registrationLayout>
