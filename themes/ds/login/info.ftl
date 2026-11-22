<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    <#if messageHeader?? && messageHeader?has_content>
      ${kcSanitize(msg(messageHeader))?no_esc}
    <#else>
      Check your email
    </#if>
  <#elseif section = "form">
    <p class="sub"><#if message?? && message.summary?has_content>${kcSanitize(message.summary)?no_esc}<#else>Continue from the message we sent.</#if><#if requiredActions??><#list requiredActions as reqActionItem> ${kcSanitize(msg("requiredAction.${reqActionItem}"))?no_esc}<#sep>, </#list></#if></p>
    <#if !skipLink??>
      <#if pageRedirectUri?has_content>
        <a class="primary" href="${pageRedirectUri}">${msg("backToApplication")}</a>
      <#elseif actionUri?has_content>
        <a class="primary" href="${actionUri}">${msg("proceedWithAction")}</a>
      <#elseif client?? && client.baseUrl?has_content>
        <a class="primary" href="${client.baseUrl}">${msg("backToApplication")}</a>
      </#if>
    </#if>
  </#if>
</@layout.registrationLayout>
