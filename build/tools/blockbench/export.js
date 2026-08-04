// export.js — 导出当前项目为 .bbmodel JSON 文件
// 用法: node export.js --out <path.bbmodel> [--connect]
const { chromium } = require("./pw");

const CHROME = "/data/data/com.termux/files/usr/bin/chromium-browser";
const URL = process.env.BLOCKBENCH_URL || "https://web.blockbench.net/";
const args = ["--no-sandbox", "--enable-unsafe-swiftshader", "--disable-dev-shm-usage"];
const fs = require("fs");

(async () => {
	const argv = process.argv.slice(2);
	const outIdx = argv.indexOf("--out");
	const out = outIdx >= 0 ? argv[outIdx + 1] : "build/previews/out.bbmodel";
	const connect = argv.includes("--connect");

	let browser;
	if (connect) {
		browser = await chromium.connectOverCDP("http://127.0.0.1:9222");
	} else {
		browser = await chromium.launch({ executablePath: CHROME, headless: true, args });
	}
	const pages = browser.contexts().flatMap((c) => c.pages());
	const page = pages[0] || (await browser.newPage());
	if (!connect) {
		await page.goto(URL, { waitUntil: "domcontentloaded", timeout: 90000 });
	}
	await page.waitForFunction(
		() => typeof Blockbench !== "undefined" && typeof Project !== "undefined",
		null,
		{ timeout: 60000 }
	);

	const data = await page.evaluate(() => {
		if (!Project) throw new Error("No project open — run model.js first");
		// 用 project codec 编译为可序列化数据（含 elements/outliner/animations/textures）
		return Codecs.project.compile({ raw: true });
	});
	fs.writeFileSync(out, JSON.stringify(data, null, 2));
	console.log(`saved ${out} (${(fs.statSync(out).size / 1024).toFixed(1)} KB)`);
	await browser.close();
})().catch((e) => {
	console.error(String(e));
	process.exit(1);
});
