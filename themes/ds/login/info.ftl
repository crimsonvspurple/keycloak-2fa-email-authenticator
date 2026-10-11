<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    <#if messageHeader??>
      ${kcSanitize(msg("${messageHeader}"))?no_esc}
    <#else>
      Check your email
    </#if>
  <#elseif section = "form">
    <p class="sub">${kcSanitize(message.summary)?no_esc}<#if requiredActions??><#list requiredActions as reqActionItem> ${kcSanitize(msg("requiredAction.${reqActionItem}"))?no_esc}<#sep>, </#list></#if></p>
    <#if !skipLink??>
      <#if pageRedirectUri?has_content>
        <a class="primary" href="${pageRedirectUri}">${msg("backToApplication")}</a>
      <#elseif actionUri?has_content>
        <a class="primary" href="${actionUri}">${msg("proceedWithAction")}</a>
      <#elseif (client.baseUrl)?has_content>
        <a class="primary" href="${client.baseUrl}">${msg("backToApplication")}</a>
      </#if>
    </#if>
  </#if>
</@layout.registrationLayout>
