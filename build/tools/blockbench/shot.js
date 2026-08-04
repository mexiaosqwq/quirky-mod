// shot.js — 打开 Blockbench，等待场景就绪后截图到指定文件
// 用法: node shot.js [out.png] [--wait-ms N] [--eval 'js'] 
//   --eval: 截图前在页面里执行的 JS（如等待动画播放）
const { chromium } = require("./pw");

const CHROME = "/data/data/com.termux/files/usr/bin/chromium-browser";
const URL = process.env.BLOCKBENCH_URL || "https://web.blockbench.net/";
const args = ["--no-sandbox", "--enable-unsafe-swiftshader", "--disable-dev-shm-usage"];

(async () => {
	const argv = process.argv.slice(2);
	const out = argv.find(a => !a.startsWith("--")) || "build/previews/shot.png";
	const waitMs = Number((argv.find(a => a.startsWith("--wait-ms")) || "").split("=")[1] || argv[argv.indexOf("--wait-ms") + 1] || 4000);
	const evalIdx = argv.indexOf("--eval");
	const evalJs = evalIdx >= 0 ? argv[evalIdx + 1] : null;

	const browser = await chromium.launch({ executablePath: CHROME, headless: true, args });
	const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
	await page.goto(URL, { waitUntil: "domcontentloaded", timeout: 90000 });
	await page.waitForFunction(
		() => typeof Blockbench !== "undefined" && typeof Canvas !== "undefined" && typeof Project !== "undefined",
		null,
		{ timeout: 60000 }
	);
	if (evalJs) {
		await page.evaluate(evalJs);
		await page.waitForTimeout(1500);
	}
	await page.waitForTimeout(waitMs); // 等视口首帧/动画
	await page.screenshot({ path: out });
	console.log(`saved ${out}`);
	await browser.close();
})().catch(e => {
	console.error(String(e));
	process.exit(1);
});
