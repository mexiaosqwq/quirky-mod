// texgen.js — 程序生成实体纹理 PNG
// 用法:
//   node texgen.js --uvmap ../../generated/demo_beast_uvmap.json [out.png]   — 按 UV 布局精确绘制（推荐）
//   node texgen.js --egg [out.png]                                            — 生成蛋 16×16
//   node texgen.js [out.png]                                                  — 旧版占位纹理（不推荐）
const fs = require("fs");
const { PNG } = require("pngjs");

const args = process.argv.slice(2);
const get = (flag) => { const i = args.indexOf(flag); return i >= 0 ? args[i + 1] : null; };

// ---------- 调色板（demo_beast 主题） ----------
const C = {
	main: [150, 120, 90],      // 主色棕 #96785a
	dark: [95, 70, 50],        // 深棕 #5f4632（背脊/耳/蹄）
	light: [190, 165, 130],    // 浅棕 #bea582（肚皮/脸）
	pink: [201, 160, 143],     // 耳内粉
	eye: [20, 20, 20],         // 黑
	white: [235, 230, 220],    // 眼白
};

// ---------- 工具 ----------
function fill(png, x1, y1, x2, y2, [r, g, b]) {
	const W = png.width;
	for (let y = y1; y < y2; y++) for (let x = x1; x < x2; x++) {
		const i = (y * W + x) * 4;
		png.data[i] = r; png.data[i + 1] = g; png.data[i + 2] = b; png.data[i + 3] = 255;
	}
}
function px(png, x, y, [r, g, b]) {
	if (x < 0 || y < 0 || x >= png.width || y >= png.height) return;
	const i = (y * png.width + x) * 4;
	png.data[i] = r; png.data[i + 1] = g; png.data[i + 2] = b; png.data[i + 3] = 255;
}
// 面内局部坐标（u 横向、v 纵向），dir 面 UV 四角点 [u1,v1,u2,v2]
function faceFill(png, uv, u0, v0, u1, v1, color) {
	fill(png, Math.round(uv[0] + u0), Math.round(uv[1] + v0), Math.round(uv[0] + u1), Math.round(uv[1] + v1), color);
}
// 混合渐变：底→顶 方向（v 增大方向变暗）
function faceGradV(png, uv, top, bottom, frac = 0.5) {
	const x1 = Math.round(uv[0]), y1 = Math.round(uv[1]), x2 = Math.round(uv[2]), y2 = Math.round(uv[3]);
	const split = y1 + Math.round((y2 - y1) * frac);
	fill(png, x1, y1, x2, split, top);
	fill(png, x1, split, x2, y2, bottom);
}

// ---------- 按 UV 布局绘制 ----------
function genByUvmap(uvmap) {
	const [W, H] = uvmap.texture_size;
	const png = new PNG({ width: W, height: H });
	fill(png, 0, 0, W, H, C.main);

	const rnd = (() => { let seed = 7; return () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff; })();

	for (const el of uvmap.elements) {
		const f = el.faces;
		const name = el.name;
		const isHead = /head/.test(name);
		const isEar = /ear/.test(name);
		const isLeg = /leg/.test(name);
		const isTail = /tail/.test(name);
		const isBody = /body/.test(name);

		for (const [dir, uv] of Object.entries(f)) {
			if (dir === "up") {
				// 顶面：背脊深色 + 斑纹
				if (isBody || isHead) {
					fill(png, ...uv, C.dark);
					// 斑纹点
					for (let i = 0; i < Math.max(2, (uv[2] - uv[0]) / 2); i++) {
						px(png, uv[0] + 1 + Math.floor(rnd() * (uv[2] - uv[0] - 2)), uv[1] + 1 + Math.floor(rnd() * (uv[3] - uv[1] - 2)), C.light);
					}
				} else {
					fill(png, ...uv, isEar ? C.pink : C.main);
				}
			} else if (dir === "down") {
				fill(png, ...uv, isEar ? C.pink : C.light); // 肚皮浅色
			} else if (dir === "north") {
				// 前面：脸 / 身体前侧
				if (isHead) {
					fill(png, ...uv, C.main);
					const [ux, uy, ux2, uy2] = uv;
					const cw = ux2 - ux, ch = uy2 - uy;
					// 眼睛（两只，上 1/3 处）
					const ey = uy + Math.round(ch * 0.3);
					const ex1 = ux + Math.round(cw * 0.2), ex2 = ux + Math.round(cw * 0.7);
					fill(png, ex1 - 1, ey - 1, ex1 + 1, ey + 2, C.white);
					fill(png, ex2 - 1, ey - 1, ex2 + 1, ey + 2, C.white);
					px(png, ex1, ey, C.eye); px(png, ex1 + 1, ey, C.eye);
					px(png, ex2, ey, C.eye); px(png, ex2 + 1, ey, C.eye);
					// 鼻子
					const nx = ux + Math.round(cw / 2) - 1, ny = uy + Math.round(ch * 0.8);
					fill(png, nx, ny, nx + 2, ny + 1, C.dark);
				} else if (isBody) {
					faceGradV(png, uv, C.main, C.light, 0.55);
				} else {
					fill(png, ...uv, C.main);
				}
			} else if (dir === "south") {
				if (isBody) faceGradV(png, uv, C.main, C.light, 0.55);
				else fill(png, ...uv, C.main);
			} else if (dir === "east" || dir === "west") {
				// 侧面（含左右）：下半渐变；耳朵内侧粉
				if (isEar) {
					// 耳内面：east=朝头（左耳）、west=朝头（右耳）→ 简化：靠头侧粉色
					fill(png, ...uv, C.pink);
				} else if (isLeg) {
					faceGradV(png, uv, C.main, C.dark, 0.66); // 腿下部深（蹄）
				} else {
					fill(png, ...uv, C.main);
				}
			}
		}
	}
	return png;
}

// ---------- 生成蛋 ----------
function genEgg() {
	const png = new PNG({ width: 16, height: 16 });
	fill(png, 0, 0, 16, 16, C.main);
	fill(png, 2, 2, 8, 9, C.light); // 高光
	let seed = 42;
	const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
	for (let i = 0; i < 22; i++) px(png, 1 + Math.floor(rnd() * 14), 1 + Math.floor(rnd() * 14), C.dark);
	fill(png, 0, 14, 16, 16, C.dark);
	return png;
}

// ---------- 入口 ----------
const uvmapPath = get("--uvmap");
let png, label;
if (uvmapPath) {
	png = genByUvmap(JSON.parse(fs.readFileSync(uvmapPath, "utf-8")));
	label = "uvmap";
} else if (args[0] === "--egg") {
	png = genEgg();
	label = "egg";
} else {
	const p = new PNG({ width: 64, height: 64 });
	fill(p, 0, 0, 64, 64, C.main);
	png = p;
	label = "placeholder";
}
const out = get("--out") || get("--uvmap")
	? (get("--out") || "../../previews/demo_beast_tex.png")
	: (args[0] === "--egg" ? (get("--egg") || "../../previews/demo_beast_spawn_egg.png") : (args[0] || "../../previews/demo_beast_tex.png"));
fs.writeFileSync(out, PNG.sync.write(png));
console.log(`wrote ${out} (${label}, ${png.width}x${png.height})`);
