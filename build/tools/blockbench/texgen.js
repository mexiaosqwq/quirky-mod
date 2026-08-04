// texgen.js — 程序生成 64×64 实体纹理 PNG（demo_beast：棕色身体+深色背脊+眼睛+浅色肚皮）
// 用法: node texgen.js [out.png]
const fs = require("fs");
const { PNG } = require("pngjs");

const W = 64, H = 64;
const png = new PNG({ width: W, height: H });
const px = (x, y, r, g, b) => {
	const i = (y * W + x) * 4;
	png.data[i] = r; png.data[i + 1] = g; png.data[i + 2] = b; png.data[i + 3] = 255;
};
// 默认底色：浅灰绿（占位，稍后手绘）
const BASE = [150, 120, 90];     // 棕
const DARK = [95, 70, 50];       // 深棕（背脊/耳朵/腿下部）
const BELLY = [190, 165, 130];   // 浅棕（肚皮）
const EYE = [20, 20, 20];        // 黑（眼睛）
for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) px(x, y, BASE[0], BASE[1], BASE[2]);

// 简单图案：中部横向背脊条 + 两侧浅色
for (let y = 12; y < 30; y++) for (let x = 8; x < 56; x++) {
	px(x, y, y < 22 ? DARK[0] : BASE[0], y < 22 ? DARK[1] : BASE[1], y < 22 ? DARK[2] : BASE[2]);
}
for (let y = 30; y < 40; y++) for (let x = 8; x < 56; x++) {
	px(x, y, BELLY[0], BELLY[1], BELLY[2]);
}
// 眼睛（两个 2×2 黑点，头部区域）
for (let dy = 0; dy < 2; dy++) for (let dx = 0; dx < 2; dx++) {
	px(34 + dx, 18 + dy, EYE[0], EYE[1], EYE[2]);
	px(44 + dx, 18 + dy, EYE[0], EYE[1], EYE[2]);
}

const out = process.argv[2] || "build/previews/demo_beast_tex.png";
fs.writeFileSync(out, PNG.sync.write(png));
console.log(`wrote ${out}`);
