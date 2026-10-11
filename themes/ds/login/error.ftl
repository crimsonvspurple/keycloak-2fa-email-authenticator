<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    Something went wrong
  <#elseif section = "form">
    <p class="sub">${kcSanitize(message.summary)?no_esc}</p>
    <#if !skipLink?? && client?? && client.baseUrl?has_content>
      <a class="primary" id="backToApplication" href="${client.baseUrl}">${msg("backToApplication")}</a>
    </#if>
  </#if>
</@layout.registrationLayout>
