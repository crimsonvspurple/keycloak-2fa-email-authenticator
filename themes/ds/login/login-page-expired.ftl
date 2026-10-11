<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
  <#if section = "header">
    This page expired
  <#elseif section = "summary">
    <p class="sub">The sign-in page sat too long and can no longer be used.</p>
  <#elseif section = "form">
    <a class="primary" id="loginRestartLink" href="${url.loginRestartFlowUrl}">Start again</a>
    <a class="text-button" id="loginContinueLink" href="${url.loginAction}">Continue</a>
  </#if>
</@layout.registrationLayout>
