// uvlayout.js — 计算统一 UV 布局（MC 标准 box 展开模板，mcsrc ModelPart.java:287-330）
// 用法: node uvlayout.js --spec specs/demo_beast.json --out ../../generated/demo_beast_uvmap.json
// 输出: 每个 cube 每面的 4 角点 UV + texture_size。texgen/model.js/convert.js 共用，保证
//       Blockbench 视口 = 游戏内渲染 = 纹理绘制 三者一致。
const fs = require("fs");

const args = process.argv.slice(2);
const get = (flag) => { const i = args.indexOf(flag); return i >= 0 ? args[i + 1] : null; };
const spec = JSON.parse(fs.readFileSync(get("--spec"), "utf-8"));
const out = get("--out") || "../../generated/uvmap.json";
const texW = spec.texture_width || 64;
const texH = spec.texture_height || 64;

const cubes = (spec.cubes || spec.elements || []).map((c) => ({
	name: c.name,
	from: c.from, to: c.to,
	box: c.to.map((t, i) => t - c.from[i]), // [w,h,d]
}));

// 行优先排布：每 cube 格宽 = d+w+d+w+2，格高 = d+h+2，超宽换行
const rows = [];
let row = [], rowW = 0, rowH = 0;
for (const c of cubes) {
	const [w, h, d] = c.box;
	const gw = d + w + d + w + 2;
	const gh = d + h + 2;
	if (row.length && rowW + gw > texW) { rows.push({ cubes: row, w: rowW, h: rowH }); row = []; rowW = 0; rowH = 0; }
	row.push({ ...c, gw, gh });
	rowW += gw; rowH = Math.max(rowH, gh);
}
if (row.length) rows.push({ cubes: row, w: rowW, h: rowH });

const elements = [];
let y = 0;
for (const r of rows) {
	let x = 0;
	for (const c of r.cubes) {
		const [w, h, d] = c.box;
		const u = x + 1, v = y + 1; // 1px 边距
		const u0 = u, u1 = u + d, u2 = u + d + w, u22 = u + d + w + w, u3 = u + d + w + d, u4 = u + d + w + d + w;
		const v0 = v, v1 = v + d, v2 = v + d + h;
		elements.push({
			name: c.name, from: c.from, to: c.to,
			faces: {
				down:  [u1, v0, u2, v1],
				up:    [u2, v0, u22, v1],
				west:  [u0, v1, u1, v2],
				north: [u1, v1, u2, v2],
				east:  [u2, v1, u3, v2],
				south: [u3, v1, u4, v2],
			},
		});
		x += c.gw;
	}
	y += r.h;
}

fs.writeFileSync(out, JSON.stringify({ texture_size: [texW, texH], elements }, null, 1));
console.log(`wrote ${out} (${elements.length} elements, ${rows.length} rows, canvas ${texW}x${texH})`);
