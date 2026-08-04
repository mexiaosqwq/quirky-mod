// pw.js — Termux (android) 平台补丁：playwright 在 process.platform==='android' 时拒绝加载，
// 在 require 之前伪造为 linux（executablePath 已显式指定系统 chromium，不触发下载）。
Object.defineProperty(process, "platform", { value: "linux" });
module.exports = require("playwright");
