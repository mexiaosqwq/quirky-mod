// pipeline.js — 一键重放：单会话内完成 建项目/骨骼/cube → 动画 → 多时间点截图 → 导出 .bbmodel
// 用法:
//   node pipeline.js --spec specs/demo_beast.json --anims specs/demo_beast_anims.json \
//     --shots "0.0:walk,0.5:walk,1.0:walk" --shot-dir ../../previews/ --export ../../models/demo_beast.bbmodel
//   --shots "t1:anim,t2:anim,...": 截图时间点与要播放的动画名（用 _ 表示无）
const { chromium } = require("./pw");

const CHROME = "/data/data/com.termux/files/usr/bin/chromium-browser";
const URL = process.env.BLOCKBENCH_URL || "https://web.blockbench.net/";
const args = ["--no-sandbox", "--enable-unsafe-swiftshader", "--disable-dev-shm-usage"];
const fs = require("fs");

(async () => {
	const argv = process.argv.slice(2);
	const get = (flag) => { const i = argv.indexOf(flag); return i >= 0 ? argv[i + 1] : null; };
	const spec = JSON.parse(fs.readFileSync(get("--spec"), "utf-8"));
	const animsFile = get("--anims");
	const anims = animsFile ? JSON.parse(fs.readFileSync(animsFile, "utf-8")) : { animations: [] };
	const shotDir = get("--shot-dir") || "../../previews/";
	const exportPath = get("--export");
	const shots = (get("--shots") || "").split(",").filter(Boolean).map(s => {
		const [t, a] = s.split(":");
		return { time: parseFloat(t), anim: a === "_" ? null : a };
	});
	const texPath = get("--tex");
	const texDataUrl = texPath ? "data:image/png;base64," + fs.readFileSync(texPath).toString("base64") : null;

	const browser = await chromium.launch({ executablePath: CHROME, headless: true, args });
	const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
	await page.goto(URL, { waitUntil: "domcontentloaded", timeout: 90000 });
	await page.waitForFunction(
		() => typeof Blockbench !== "undefined" && typeof Project !== "undefined" && typeof Canvas !== "undefined" && typeof Formats !== "undefined" && typeof Animator !== "undefined" && typeof Animation !== "undefined",
		null,
		{ timeout: 60000 }
	);

	const modelResult = await page.evaluate(({ s, texDataUrl }) => {
		if (!Project) newProject(Formats.bedrock);
		[...Outliner.elements].forEach((el) => el.remove());
		if (texDataUrl) {
			// 创建/复用纹理并注入生成的 PNG
			let tex = Texture.getDefault();
			if (!tex) {
				tex = new Texture({ name: "demo_beast", width: 64, height: 64 }).add();
			}
			tex.fromDataURL(texDataUrl);
			tex.width = 64;
			tex.height = 64;
			tex.setAsDefaultTexture();
			Canvas.updateAllUVs && Canvas.updateAllUVs();
		}
		const boneMap = {};
		for (const b of s.bones || []) {
			const g = new Group({ name: b.name, origin: b.origin || [0, 0, 0] }).init();
			if (b.parent && boneMap[b.parent]) g.addTo(boneMap[b.parent]);
			else g.addTo("root");
			boneMap[b.name] = g;
		}
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
		return { format: Project.format ? Project.format.id : "?", elements: Outliner.elements.length, added };
	}, { s: spec, texDataUrl });

	const animResult = await page.evaluate(({ anims }) => {
		[...Animation.all].forEach((a) => a.remove());
		const created = [];
		for (const anim of anims.animations || []) {
			const data = {
				format_version: "1.8.0",
				animations: {
					[`animation.${anim.name}`]: {
						loop: !!anim.loop,
						animation_length: anim.length,
						bones: Object.fromEntries(
							Object.entries(anim.bones || {}).map(([bone, kfs]) => {
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
			created.push(anim.name);
		}
		return { created, total: Animation.all.length };
	}, { anims });

	// 截图（多时间点，切到动画模式并定位时间）
	await page.evaluate(() => {
		if (Modes.selected && Modes.selected.id !== "animate" && Modes.options.animate) {
			Modes.options.animate.select();
		}
	});
	for (const shot of shots) {
		await page.evaluate(({ time, anim }) => {
			if (anim) {
				const a = Animation.all.find((x) => x.name === anim || x.name === "animation." + anim);
				if (a) {
					if (!a.selected) a.select();
					Timeline.setTime(time);
					Animator.preview();
				}
			}
		}, shot);
		await page.waitForTimeout(3500);
		const out = `${shotDir}${spec.name || "model"}_${String(shot.time).replace(".", "_")}${shot.anim ? "_" + shot.anim : ""}.png`;
		await page.screenshot({ path: out });
		console.log(`shot ${out}`);
	}

	if (exportPath) {
		const data = await page.evaluate(() => Codecs.project.compile({ raw: true }));
		fs.writeFileSync(exportPath, JSON.stringify(data, null, 2));
		console.log(`exported ${exportPath} (${(fs.statSync(exportPath).size / 1024).toFixed(1)} KB)`);
	}

	console.log(JSON.stringify({ model: modelResult, anim: animResult }));
	await browser.close();
})().catch((e) => {
	console.error(String(e));
	process.exit(1);
});
