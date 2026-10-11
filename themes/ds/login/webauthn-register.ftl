<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
  <#if section = "header">
    Create a passkey
  <#elseif section = "summary">
    <p class="sub">This device can sign you in next time. You can skip this.</p>
  <#elseif section = "form">
    <form id="register" action="${url.loginAction}" method="post">
      <input type="hidden" id="clientDataJSON" name="clientDataJSON">
      <input type="hidden" id="attestationObject" name="attestationObject">
      <input type="hidden" id="publicKeyCredentialId" name="publicKeyCredentialId">
      <input type="hidden" id="authenticatorLabel" name="authenticatorLabel">
      <input type="hidden" id="transports" name="transports">
      <input type="hidden" id="authenticatorAttachment" name="authenticatorAttachment">
      <input type="hidden" id="error" name="error">
    </form>
    <button class="primary" type="button" id="registerWebAuthn">Create a passkey</button>
    <form action="${url.loginAction}" method="post">
      <button class="text-button" type="submit" name="skip" value="on">Skip for now</button>
    </form>
    <script type="module">
      <#outputformat "JavaScript">
      import { registerByWebAuthn } from "${url.resourcesPath}/js/webauthnRegister.js";
      const registerButton = document.getElementById("registerWebAuthn");
      if (registerButton) {
        registerButton.addEventListener("click", function () {
          registerByWebAuthn({
            challenge: ${challenge?c},
            userid: ${userid?c},
            username: ${username?c},
            signatureAlgorithms: [<#list signatureAlgorithms as sigAlg>${sigAlg?c},</#list>],
            rpEntityName: ${rpEntityName?c},
            rpId: ${rpId?c},
            attestationConveyancePreference: ${attestationConveyancePreference?c},
            authenticatorAttachment: ${authenticatorAttachment?c},
            requireResidentKey: ${requireResidentKey?c},
            residentKey: ${residentKey?c},
            userVerificationRequirement: ${userVerificationRequirement?c},
            createTimeout: ${createTimeout?c},
            excludeCredentialIds: ${excludeCredentialIds?c},
            initLabel: ${msg("webauthn-registration-init-label")?c},
            initLabelPrompt: ${msg("webauthn-registration-init-label-prompt")?c},
            errmsg: ${msg("webauthn-unsupported-browser-text")?c}
          });
        });
      }
      </#outputformat>
    </script>
  </#if>
</@layout.registrationLayout>
