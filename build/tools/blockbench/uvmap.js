// uvmap.js — 导出当前 Blockbench 项目的 UV 布局（每个 cube 每个 face 的纹理坐标）
// 用法:
//   node uvmap.js --connect --out ../../generated/demo_beast_uvmap.json
//   （--connect 连桌面实例；否则 headless 新建空项目）
// 输出 JSON 供 texgen.js --uvmap 按真实 UV 绘制纹理。
const { chromium } = require("./pw");
const fs = require("fs");

const CHROME = "/data/data/com.termux/files/usr/bin/chromium-browser";
const URL = process.env.BLOCKBENCH_URL || "https://web.blockbench.net/";
const args = ["--no-sandbox", "--enable-unsafe-swiftshader", "--disable-dev-shm-usage"];

(async () => {
	const argv = process.argv.slice(2);
	const get = (flag) => { const i = argv.indexOf(flag); return i >= 0 ? argv[i + 1] : null; };
	const out = get("--out") || "../../generated/uvmap.json";
	const connect = argv.includes("--connect");

	const browser = connect
		? await chromium.connectOverCDP("http://127.0.0.1:9222")
		: await chromium.launch({ executablePath: CHROME, headless: true, args });
	const page = connect
		? browser.contexts().flatMap((c) => c.pages())[0]
		: await browser.newPage({ viewport: { width: 1280, height: 720 } });
	if (!connect) {
		await page.goto(URL, { waitUntil: "domcontentloaded", timeout: 90000 });
		await page.waitForFunction(() => typeof Project !== "undefined" && typeof Canvas !== "undefined", null, { timeout: 60000 });
	}

	const result = await page.evaluate(() => {
		if (!Project) return { error: "no project open" };
		const size = [Project.texture_width, Project.texture_height];
		const elements = [];
		for (const el of Outliner.elements) {
			if (!el.from || !el.to) continue;
			const faces = {};
			for (const [dir, f] of Object.entries(el.faces || {})) {
				faces[dir] = { uv: f.uv ? [...f.uv] : null, rotation: f.rotation || 0 };
			}
			elements.push({ name: el.name, from: [...el.from], to: [...el.to], faces });
		}
		return { texture_size: size, elements };
	});
	if (result.error) throw new Error(result.error);
	fs.writeFileSync(out, JSON.stringify(result, null, 1));
	console.log(`wrote ${out} (${result.elements.length} elements)`);
	await browser.close();
})().catch((e) => { console.error(String(e)); process.exit(1); });
