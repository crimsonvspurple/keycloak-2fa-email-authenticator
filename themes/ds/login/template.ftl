<#function dsSiteName>
  <#if !client??>
    <#return "">
  </#if>
  <#local raw = (client.name!"")?trim>
  <#if raw?ends_with(" web")>
    <#local raw = raw?substring(0, raw?length - 4)>
  </#if>
  <#local id = (client.clientId!"")>
  <#if raw?has_content && raw != id>
    <#return raw>
  </#if>
  <#if id == "gp-web"><#return "Goaliepost"></#if>
  <#if id == "dn-web"><#return "Dobbernomics"></#if>
  <#if id == "geek-web"><#return "Geek"></#if>
  <#return raw>
</#function>
<#function dsSiteUrl>
  <#if !client??>
    <#return "">
  </#if>
  <#local base = (client.baseUrl!"")?trim>
  <#if base?has_content && base != "/">
    <#return base?remove_ending("/")>
  </#if>
  <#local id = (client.clientId!"")>
  <#if id == "gp-web"><#return "https://new.goaliepost.com"></#if>
  <#if id == "dn-web"><#return "https://new.dobbernomics.com"></#if>
  <#if id == "geek-web"><#return "https://new-geek.dobbersports.com"></#if>
  <#return "">
</#function>
<#macro dsPasskeySwitch>
  <label class="switch">
    <span class="switch-control">
      <input name="create-passkey" type="checkbox" value="on" role="switch">
      <span class="switch-track" aria-hidden="true"></span>
    </span>
    <span class="switch-copy">Create a passkey on this device</span>
  </label>
</#macro>
<#macro dsOtpField>
  <div class="field otp-field">
    <label class="micro" for="code">Code</label>
    <div class="otp">
      <#list 1..6 as _digit>
        <span class="otp-cell" aria-hidden="true"><span class="otp-digit"></span></span>
      </#list>
      <input id="code" class="otp-input" name="code" type="text" inputmode="numeric"
             autocomplete="one-time-code" enterkeyhint="done"
             autocapitalize="off" autocorrect="off" spellcheck="false"
             pattern="[0-9]{6}" maxlength="8" autofocus required
             aria-label="6-digit code">
    </div>
  </div>
</#macro>
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false pageTitle="Sign in">
<!DOCTYPE html>
<html lang="${lang}" data-theme="light">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light dark">
  <title>${pageTitle} · DobberSports</title>
  <script>
    (function () {
      var stored = null;
      try { stored = localStorage.getItem("ds-theme"); } catch (e) {}
      var theme = stored === "light" || stored === "dark"
        ? stored
        : (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
      document.documentElement.setAttribute("data-theme", theme);
    })();
  </script>
  <link rel="stylesheet" href="${url.resourcesPath}/css/ds.css?v=${properties.dsVersion}">
  <script type="importmap">
    {
      "imports": {
        "rfc4648": "${url.resourcesCommonPath}/vendor/rfc4648/rfc4648.js"
      }
    }
  </script>
  <#if scripts??>
    <#list scripts as script>
      <script src="${script}" type="text/javascript"></script>
    </#list>
  </#if>
</head>
<body class="${bodyClass}">
  <button type="button" class="theme-toggle" id="ds-theme-toggle" aria-label="Switch color theme">
    <span class="theme-toggle-icon" aria-hidden="true"></span>
    <span class="theme-toggle-text">Theme</span>
  </button>
  <main class="wrap">
    <section class="card">
      <img class="logo" src="${url.resourcesPath}/img/logo.png?v=${properties.dsVersion}" alt="DobberSports" width="571" height="90">
      <h1 class="title"><#nested "header"></h1>
      <#nested "summary">

      <#if displayRequiredFields>
        <p class="sub"><span class="req" aria-hidden="true">*</span> ${msg("requiredFields")}</p>
      </#if>

      <#if auth?has_content && auth.showUsername() && !auth.showResetCredentials() && !dsEmail?has_content>
        <#nested "show-username">
        <#if auth.attemptedUsername?has_content>
          <p class="attempted">${auth.attemptedUsername}</p>
        </#if>
      </#if>

      <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
        <p class="alert alert-${message.type}" role="${(message.type = 'error')?then('alert','status')}">
          ${kcSanitize(message.summary)?no_esc}
        </p>
      </#if>

      <#nested "socialProviders">
      <#nested "form">

      <#if auth?has_content && auth.showTryAnotherWayLink()>
        <form class="stack" action="${url.loginAction}" method="post">
          <input type="hidden" name="tryAnotherWay" value="on">
          <button class="text-button" type="submit">${msg("doTryAnotherWay")}</button>
        </form>
      </#if>

      <#if displayInfo>
        <div class="info"><#nested "info"></div>
      </#if>
    </section>
    <#if dsSiteUrl()?has_content>
      <a class="back-link" href="${dsSiteUrl()}">Cancel and go back to ${dsSiteName()}</a>
    </#if>
  </main>
  <script>
    (function () {
      var key = "ds-theme";
      var root = document.documentElement;
      var button = document.getElementById("ds-theme-toggle");
      function apply(theme) {
        root.setAttribute("data-theme", theme);
        if (!button) return;
        var next = theme === "dark" ? "light" : "dark";
        button.setAttribute("aria-label", "Switch to " + next + " theme");
        var text = button.querySelector(".theme-toggle-text");
        if (text) text.textContent = theme === "dark" ? "Light" : "Dark";
      }
      apply(root.getAttribute("data-theme") || "light");
      if (!button) return;
      button.addEventListener("click", function () {
        var next = root.getAttribute("data-theme") === "dark" ? "light" : "dark";
        try { localStorage.setItem(key, next); } catch (e) {}
        apply(next);
      });
    })();
  </script>
  <script>
    (function () {
      var input = document.getElementById("code");
      if (!input || !input.classList.contains("otp-input")) return;
      var root = input.closest(".otp");
      var cells = root ? root.querySelectorAll(".otp-cell") : [];
      var form = input.form;
      var submitter = form ? form.querySelector("button.primary[type='submit']") : null;
      var sent = false;
      function digits(value) {
        return String(value || "").replace(/\D/g, "").slice(0, 6);
      }
      function paint(code) {
        var active = code.length < 6 ? code.length : -1;
        for (var i = 0; i < cells.length; i++) {
          var digit = cells[i].querySelector(".otp-digit");
          if (digit) digit.textContent = code.charAt(i) || "";
          cells[i].classList.toggle("filled", i < code.length);
          cells[i].classList.toggle("active", i === active);
        }
      }
      function finish() {
        if (sent || !form) return;
        sent = true;
        window.setTimeout(function () {
          if (digits(input.value).length !== 6) {
            sent = false;
            return;
          }
          try {
            if (submitter && form.requestSubmit) form.requestSubmit(submitter);
            else form.submit();
          } catch (e) {
            sent = false;
          }
        }, 0);
      }
      function apply(raw) {
        var code = digits(raw);
        if (input.value !== code) input.value = code;
        paint(code);
        if (code.length === 6) finish();
        else sent = false;
      }
      input.addEventListener("input", function () { apply(input.value); });
      input.addEventListener("change", function () { apply(input.value); });
      input.addEventListener("paste", function (event) {
        var text = event.clipboardData && event.clipboardData.getData("text");
        if (text == null) return;
        event.preventDefault();
        apply(text);
      });
      var watch = 0;
      function arm() {
        if (watch) return;
        var last = digits(input.value);
        var tries = 0;
        watch = window.setInterval(function () {
          tries += 1;
          var code = digits(input.value);
          if (code !== last) {
            last = code;
            apply(code);
          }
          if (sent || tries > 40) {
            window.clearInterval(watch);
            watch = 0;
          }
        }, 100);
      }
      input.addEventListener("focus", function () {
        if (root) root.classList.add("is-focus");
        paint(digits(input.value));
        arm();
      });
      input.addEventListener("blur", function () {
        if (root) root.classList.remove("is-focus");
        if (watch) {
          window.clearInterval(watch);
          watch = 0;
        }
      });
      apply(input.value);
      if (document.activeElement === input) arm();
    })();
  </script>
</body>
</html>
</#macro>
