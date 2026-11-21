<#macro conditionalUIData>
    <#if enableWebAuthnConditionalUI?has_content>
        <form id="webauth" action="${url.loginAction}" method="post">
            <input type="hidden" id="clientDataJSON" name="clientDataJSON"/>
            <input type="hidden" id="authenticatorData" name="authenticatorData"/>
            <input type="hidden" id="signature" name="signature"/>
            <input type="hidden" id="credentialId" name="credentialId"/>
            <input type="hidden" id="userHandle" name="userHandle"/>
            <input type="hidden" id="error" name="error"/>
        </form>
        <#if authenticators??>
            <form id="authn_select" class="${properties.kcFormClass!}">
                <#list authenticators.authenticators as authenticator>
                    <input type="hidden" name="authn_use_chk" value="${authenticator.credentialId}"/>
                </#list>
            </form>
        </#if>
        <script type="module">
           <#outputformat "JavaScript">
           import { authenticateByWebAuthn } from "${url.resourcesPath}/js/webauthnAuthenticate.js";
           import { initAuthenticate } from "${url.resourcesPath}/js/passkeysConditionalAuth.js";

           const args = {
               isUserIdentified : ${isUserIdentified},
               challenge : ${challenge?c},
               userVerification : ${userVerification?c},
               rpId : ${rpId?c},
               createTimeout : ${createTimeout?c},
               mediation : ${(mediation!'conditional')?c},
               authenticatorAttachment : ${authenticatorAttachment?c},
           };

           document.addEventListener("DOMContentLoaded", (event) => initAuthenticate({errmsg : ${msg("passkey-unsupported-browser-text")?c}, ...args}));
           const authButton = document.getElementById('authenticateWebAuthnButton');
           if (authButton) {
               authButton.addEventListener("click", (event) => {
                   event.preventDefault();
                   authenticateByWebAuthn({errmsg : ${msg("webauthn-unsupported-browser-text")?c}, ...args});
               }, { once: true });
           }
           </#outputformat>
        </script>
        <a id="authenticateWebAuthnButton" class="social-btn" href="#" data-alias="passkey">
            <svg class="social-mark" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                <path fill="currentColor" fill-rule="evenodd" d="M12.65 10C11.83 7.67 9.61 6 7 6 3.69 6 1 8.69 1 12s2.69 6 6 6c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z"/>
            </svg>
            <span class="social-label">Passkey</span>
        </a>
    </#if>
</#macro>
