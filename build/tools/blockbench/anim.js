// anim.js — 在 Blockbench 中创建/清空动画（关键帧 position/rotation/scale）
// 用法:
//   node anim.js --add --json '{"name":"walk","loop":true,"length":2,"bones":{"leg_l":[{"time":0,"rotation":[0,0,30]},...]}}' [--clear]
//   --clear: 先删除项目现有动画（重跑时避免重复）
//   --connect: 连接桌面实例（实时显示）
const { chromium } = require("./pw");

const CHROME = "/data/data/com.termux/files/usr/bin/chromium-browser";
const URL = process.env.BLOCKBENCH_URL || "https://web.blockbench.net/";
const args = ["--no-sandbox", "--enable-unsafe-swiftshader", "--disable-dev-shm-usage"];

(async () => {
	const argv = process.argv.slice(2);
	const jsonIdx = argv.indexOf("--json");
	const anim = jsonIdx >= 0 ? JSON.parse(argv[jsonIdx + 1]) : null;
	const doClear = argv.includes("--clear");
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
		() => typeof Blockbench !== "undefined" && typeof Animator !== "undefined" && typeof Animation !== "undefined",
		null,
		{ timeout: 60000 }
	);

	const result = await page.evaluate(({ anim, doClear }) => {
		if (!Project) throw new Error("No project open — run model.js first");
		if (doClear) {
			[...Animation.all].forEach((a) => a.remove());
		}
		const anims = anim && Array.isArray(anim.animations) ? anim.animations : (anim ? [anim] : []);
		if (!anims.length) return { cleared: doClear, remaining: Animation.all.length };
		const created = [];
		for (const a of anims) {
			const data = {
				format_version: "1.8.0",
				animations: {
					[`animation.${a.name}`]: {
						loop: !!a.loop,
						animation_length: a.length,
						bones: Object.fromEntries(
							Object.entries(a.bones || {}).map(([bone, kfs]) => {
								const ch = { position: {}, rotation: {}, scale: {} };
								for (const kf of kfs) {
									if (kf.position) ch.position[kf.time] = kf.position;
									if (kf.rotation) ch.rotation[kf.time] = kf.rotation;
									if (kf.scale) ch.scale[kf.time] = kf.scale;
								}
								return [bone, ch];
							})
						),
					},
				},
			};
			Animator.loadFile({ content: JSON.stringify(data) });
			created.push(a.name);
		}
		return { added: created, total: Animation.all.length };
	}, { anim, doClear });

	console.log(JSON.stringify(result));
	await browser.close();
})().catch((e) => {
	console.error(String(e));
	process.exit(1);
});
