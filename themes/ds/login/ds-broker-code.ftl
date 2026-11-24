<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    Confirm your email
  <#elseif section = "summary">
    <p class="sub">${dsIdpName!"This service"} uses <span class="attempted">${dsEmail!}</span>. That address already has an account, and it is not confirmed yet. We sent a code to it. Enter the code to confirm the email and finish signing in. The link in the email works in this browser.</p>
  <#elseif section = "form">
    <#if message?? && message.summary?has_content>
      <p class="alert alert-${message.type!'error'}" role="${((message.type!'') = 'error')?then('alert','status')}">
        ${kcSanitize(message.summary)?no_esc}
      </p>
    </#if>
    <form class="stack" action="${url.loginAction}" method="post" novalidate>
      <@layout.dsOtpField />
      <@layout.dsTurnstile "otp" />
      <button class="primary" name="login" type="submit">Continue</button>
      <@layout.dsResendButton />
      <a class="text-button" href="${url.loginRestartFlowUrl}">Back to sign in</a>
    </form>
  </#if>
</@layout.registrationLayout>
