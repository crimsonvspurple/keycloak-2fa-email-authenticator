<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    <#if dsIdpFailure!false>
      Couldn't sign you in
    <#else>
      Something went wrong
    </#if>
  <#elseif section = "form">
    <#if dsIdpFailure!false>
      <#if (dsIdpDisplayName!"")?has_content>
        <#assign provider = dsIdpDisplayName>
        <p class="sub" id="ds-idp-failure">We couldn't sign you in with ${provider}. Your ${provider} account may be suspended or restricted, or ${provider} is temporarily unavailable. Please try another sign-in method.</p>
      <#else>
        <p class="sub" id="ds-idp-failure">We couldn't sign you in with this service. Your account may be suspended or restricted, or the service is temporarily unavailable. Please try another sign-in method.</p>
      </#if>
    <#elseif message?? && message.summary?has_content>
      <p class="sub">${kcSanitize(message.summary)?no_esc}</p>
    <#else>
      <p class="sub">Something went wrong. Please try again.</p>
    </#if>
    <#if url??>
      <a class="primary" id="loginRestartLink" href="${url.loginRestartFlowUrl}">Back to sign in</a>
    </#if>
    <#if !skipLink?? && client?? && client.baseUrl?has_content>
      <a class="text-button" id="backToApplication" href="${client.baseUrl}">${msg("backToApplication")}</a>
    </#if>
  </#if>
</@layout.registrationLayout>
