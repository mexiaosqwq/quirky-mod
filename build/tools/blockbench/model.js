// model.js — 在 Blockbench 网页版中建立项目/骨骼/Cube，可选截图
// 用法:
//   node model.js --init --json '{"bones":[...],"cubes":[...]}' [--screenshot out.png] [--wait-ms N]
// spec 结构:
//   bones: [{name, parent?, origin?:[x,y,z]}]
//   cubes: [{name, bone?, from:[x,y,z], to:[x,y,z], origin?:[x,y,z], uv?:[u,v]}]
const { chromium } = require("./pw");

const CHROME = "/data/data/com.termux/files/usr/bin/chromium-browser";
const URL = process.env.BLOCKBENCH_URL || "https://web.blockbench.net/";
const args = ["--no-sandbox", "--enable-unsafe-swiftshader", "--disable-dev-shm-usage"];

(async () => {
	const argv = process.argv.slice(2);
	const jsonIdx = argv.indexOf("--json");
	const spec = jsonIdx >= 0 ? JSON.parse(argv[jsonIdx + 1]) : null;
	const shotIdx = argv.indexOf("--screenshot");
	const out = shotIdx >= 0 ? argv[shotIdx + 1] : null;
	const waitIdx = argv.indexOf("--wait-ms");
	const waitMs = waitIdx >= 0 ? Number(argv[waitIdx + 1]) : 3000;
	const connect = argv.includes("--connect");

	let browser;
	if (connect) {
		// 连接已运行的桌面实例（remote-debugging-port 9222），用户可实时看到模型
		browser = await chromium.connectOverCDP("http://127.0.0.1:9222");
	} else {
		browser = await chromium.launch({ executablePath: CHROME, headless: true, args });
	}
	const pages = browser.contexts().flatMap((c) => c.pages());
	const page = pages[0] || (await browser.newPage());
	if (!connect) {
		await page.goto(URL, { waitUntil: "domcontentloaded", timeout: 90000 });
	}
	await page.bringToFront().catch(() => {});
	await page.waitForFunction(
		() => typeof Blockbench !== "undefined" && typeof Project !== "undefined" && typeof Canvas !== "undefined" && typeof Formats !== "undefined",
		null,
		{ timeout: 60000 }
	);

	if (spec) {
		const result = await page.evaluate((s) => {
			// 1. 项目：没有则用 Bedrock Entity 格式新建（全局 newProject）；有则清空元素重建
			if (!Project) {
				newProject(Formats.bedrock);
			}
			const model = Project;
			[...Outliner.elements].forEach((el) => el.remove());
			// 2. 骨骼（Group）
			const boneMap = {};
			for (const b of s.bones || []) {
				const g = new Group({ name: b.name, origin: b.origin || [0, 0, 0] }).init();
				if (b.parent && boneMap[b.parent]) g.addTo(boneMap[b.parent]);
				else g.addTo("root");
				boneMap[b.name] = g;
			}
			// 3. 立方体
			const tex = Texture.getDefault();
			const added = [];
			for (const c of s.cubes || []) {
				const cube = new Cube({
					autouv: c.uv ? 0 : 1,
					name: c.name,
					from: c.from,
					to: c.to,
					origin: c.origin || c.from,
				}).init();
				cube.addTo((c.bone && boneMap[c.bone]) || "root");
				if (c.uv) cube.extend({ uv_offset: c.uv });
				cube.applyTexture(tex, true);
				cube.mapAutoUV();
				added.push(cube.name);
			}
			Canvas.updateAll();
			return { model: model.name, format: model.format ? model.format.id : String(model.format), elements: Outliner.elements.length, added };
		}, spec);
		console.log(JSON.stringify(result));
	}

	if (out) {
		await page.waitForTimeout(waitMs);
		await page.screenshot({ path: out });
		console.log(`saved ${out}`);
	}
	await browser.close();
})().catch(e => {
	console.error(String(e));
	process.exit(1);
});
