// launch.js — 启动 headless chromium 打开 Blockbench 网页版，等待加载完成
// 用法: node launch.js
// 输出: {"ok":true,"webgl":true,"title":"Blockbench"}（JSON 一行）
const { chromium } = require("./pw");

const CHROME = "/data/data/com.termux/files/usr/bin/chromium-browser";
const URL = process.env.BLOCKBENCH_URL || "https://web.blockbench.net/";
const args = ["--no-sandbox", "--enable-unsafe-swiftshader", "--disable-dev-shm-usage"];

(async () => {
	const browser = await chromium.launch({ executablePath: CHROME, headless: true, args });
	const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
	await page.goto(URL, { waitUntil: "domcontentloaded", timeout: 90000 });
	await page.waitForFunction(() => typeof Blockbench !== "undefined", null, { timeout: 60000 });
	await page.waitForFunction(
		() => typeof Project !== "undefined" && typeof Canvas !== "undefined",
		null,
		{ timeout: 60000 }
	);
	const webgl = await page.evaluate(() => {
		const c = document.createElement("canvas");
		return !!(c.getContext("webgl2") || c.getContext("webgl"));
	});
	console.log(JSON.stringify({ ok: true, webgl, title: await page.title() }));
	await browser.close();
})().catch(e => {
	console.error(JSON.stringify({ ok: false, error: String(e) }));
	process.exit(1);
});
