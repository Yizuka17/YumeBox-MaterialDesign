function main(config) {
  if (!config["rule-providers"]) {
    config["rule-providers"] = {};
  }
  config["rule-providers"]["prevent_dns_leak"] = {
    type: "http",
    interval: 86400,
    behavior: "domain",
    format: "text",
    url: "https://raw.githubusercontent.com/xishang0128/rules/main/clash%20or%20stash/prevent_dns_leak/prevent_dns_leak_domain.list",
  };

  if (!Array.isArray(config.rules)) {
    config.rules = [];
  }
  const matchRule = config.rules.find(function (rule) {
    return typeof rule === "string" && rule.startsWith("MATCH");
  });
  const matchOutbound = matchRule ? matchRule.split(",").pop() : null;
  if (matchOutbound) {
    const leakRule = "RULE-SET,prevent_dns_leak," + matchOutbound;
    const alreadyPresent = config.rules.some(function (rule) {
      return typeof rule === "string" && rule.indexOf("RULE-SET,prevent_dns_leak,") === 0;
    });
    if (!alreadyPresent) {
      config.rules.unshift(leakRule);
    }
  }

  if (!config.dns) {
    config.dns = {};
  }
  if (config.dns["enhanced-mode"] !== "fake-ip") {
    config.dns["enhanced-mode"] = "fake-ip";
  }

  return config;
}