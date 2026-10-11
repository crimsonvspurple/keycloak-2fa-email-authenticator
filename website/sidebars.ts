import type { SidebarsConfig } from "@docusaurus/plugin-content-docs";

const sidebars: SidebarsConfig = {
  docs: [
    "intro",
    "get-started",
    "for-ai-agents",
    {
      type: "category",
      label: "Installation",
      collapsed: false,
      items: ["installation/local", "installation/docker"],
    },
    {
      type: "category",
      label: "Configuration",
      collapsed: false,
      items: [
        "configuration/authentication-flow",
        "configuration/email-providers",
        "configuration/template-customization",
      ],
    },
    "local-testing",
    "localization",
    "troubleshooting",
    "contributing",
  ],
};

export default sidebars;
