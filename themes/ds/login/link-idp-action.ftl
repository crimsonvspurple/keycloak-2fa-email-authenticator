<#import "template.ftl" as layout>
<#-- Account-console linking. The back control posts cancel so Keycloak
     returns to Manage account. A plain link leaves the action hanging. -->
<#global dsAccountLink = true>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    ${msg("linkIdpActionTitle", idpDisplayName)}
  <#elseif section = "summary">
    <p class="sub">${msg("linkIdpActionMessage", idpDisplayName)}</p>
  <#elseif section = "form">
    <form class="stack" id="kc-link-idp" action="${url.loginAction}" method="post">
      <button class="primary" id="kc-continue" name="continue" type="submit">${msg("doContinue")}</button>
    </form>
  </#if>
</@layout.registrationLayout>
