<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html lang="${lang}" data-theme="light">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light dark">
  <title>Sign in · DobberSports</title>
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
  <link rel="stylesheet" href="${url.resourcesPath}/css/ds.css">
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
      <img class="logo" src="${url.resourcesPath}/img/logo.png" alt="DobberSports" width="571" height="90">
      <h1 class="title"><#nested "header"></h1>
      <#nested "summary">

      <#if displayRequiredFields>
        <p class="sub"><span class="req" aria-hidden="true">*</span> ${msg("requiredFields")}</p>
      </#if>

      <#if auth?has_content && auth.showUsername() && !auth.showResetCredentials()>
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
</body>
</html>
</#macro>
