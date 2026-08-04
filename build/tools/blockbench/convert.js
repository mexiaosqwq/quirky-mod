// convert.js — .bbmodel → 26.2 Java 实体模型/动画代码生成器
// 用法: node convert.js --in <x.bbmodel> --out <dir> [--class DemoBeast] [--package dev.quirky.client.demobeast]
// 产物: <dir>/<Class>Model.java + <dir>/<Class>Animations.java
//
// 转换规则（26.2 mcsrc 已验证）:
//  - 模型: outliner 树 → PartDefinition 层级; element → texOffs+addBox(相对骨骼 origin)
//  - 坐标: Blockbench y 向上 → MC y 向下: position y 取负; rotation x/z 取负
//  - 顶层 root 骨骼固定 PartPose.offset(0, 24, 0)（原版 FrogModel 模式）
//  - 动画: animations[].animators[].keyframes → AnimationDefinition.Builder
//          channel → Targets.POSITION/ROTATION/SCALE; 插值 linear→LINEAR, catmullrom→CATMULLROM, 其余→LINEAR
const fs = require("fs");
const path = require("path");

const argv = process.argv.slice(2);
const get = (f) => { const i = argv.indexOf(f); return i >= 0 ? argv[i + 1] : null; };
const inPath = get("--in");
const outDir = get("--out") || "build/generated/";
const cls = get("--class") || "DemoBeast";
const pkg = get("--package") || "dev.quirky.client.demobeast";

if (!inPath) { console.error("usage: convert.js --in <x.bbmodel> --out <dir> [--class X]"); process.exit(1); }

const bb = JSON.parse(fs.readFileSync(inPath, "utf-8"));
const TEX_W = bb.resolution ? bb.resolution.width : 64;
const TEX_H = bb.resolution ? bb.resolution.height : 64;
const f2 = (v) => { const n = Number(v); return Number.isInteger(n) ? `${n}.0F` : `${n}F`; };
const rad = (deg) => `${(Number(deg) * Math.PI / 180).toFixed(5)}F`;
const safeId = (n) => "part" + n.replace(/[^A-Za-z0-9_]/g, "_");

// ---- 解析节点：uuid -> {kind, name, origin, rotation, data} ----
const nodes = new Map();
for (const g of bb.groups || []) nodes.set(g.uuid, { kind: "group", name: g.name, origin: g.origin || [0, 0, 0], rotation: g.rotation || [0, 0, 0], data: g });
for (const e of bb.elements || []) nodes.set(e.uuid, { kind: "cube", name: e.name, from: e.from, to: e.to, origin: e.origin || e.from, data: e });
// outliner 树（对象=group 引用，字符串=element uuid）；groups 数组无 children 字段，层级全在 outliner
function parseOutliner(items) {
	const out = [];
	for (const it of items || []) {
		if (typeof it === "string") { out.push({ ref: it, children: [] }); continue; }
		out.push({ ref: it.uuid, children: parseOutliner(it.children || []) });
	}
	return out;
}
const outlinerTree = parseOutliner(bb.outliner);

// ---- 生成模型代码 ----
const lines = [];
const indent = "	";
const isBoxUv = bb.meta.box_uv === true;

function cubeBuilderExpr(cube, boneOrigin) {
	const [fx, fy, fz] = cube.from, [tx, ty, tz] = cube.to;
	const x = fx - boneOrigin[0], y = fy - boneOrigin[1], z = fz - boneOrigin[2];
	const w = tx - fx, h = ty - fy, d = tz - fz;
	let u = 0, v = 0;
	if (isBoxUv && cube.data.faces && cube.data.faces.north) {
		u = Number(cube.data.faces.north.uv[0]); v = Number(cube.data.faces.north.uv[1]);
	} else if (cube.data.uv_offset) {
		u = Number(cube.data.uv_offset[0]); v = Number(cube.data.uv_offset[1]);
	}
	return `CubeListBuilder.create().texOffs(${Math.round(u)}, ${Math.round(v)}).addBox(${f2(x)}, ${f2(-y)}, ${f2(z)}, ${f2(w)}, ${f2(h)}, ${f2(d)})`;
}

// 深度优先：父栈存 {varName, uuid}
function emitNode(treeNode, depth, parentStack) {
	const node = nodes.get(treeNode.ref);
	if (!node) return;
	const pad = indent.repeat(depth);
	const parent = parentStack[parentStack.length - 1];
	if (node.kind === "group") {
		const [ox, oy, oz] = node.origin, [rx, ry, rz] = node.rotation;
		const parentOrigin = parent.uuid ? (nodes.get(parent.uuid)?.origin || [0, 0, 0]) : [0, 0, 0];
		let pose;
		if (depth === 0) {
			// 顶层 root：固定基准（原版模式 PartPose.offset(0, 24, 0)）
			pose = "PartPose.offset(0.0F, 24.0F, 0.0F)";
		} else {
			const px = ox - parentOrigin[0], py = oy - parentOrigin[1], pz = oz - parentOrigin[2];
			pose = (rx === 0 && ry === 0 && rz === 0)
				? `PartPose.offset(${f2(px)}, ${f2(-py)}, ${f2(pz)})`
				: `PartPose.offsetAndRotation(${f2(px)}, ${f2(-py)}, ${f2(pz)}, ${rad(-rx)}, ${rad(ry)}, ${rad(-rz)})`;
		}
		const varName = safeId(node.name);
		lines.push(`${pad}PartDefinition ${varName} = ${parent.varName}.addOrReplaceChild("${node.name}", CubeListBuilder.create(), ${pose});`);
		parentStack.push({ varName, uuid: node.uuid });
		for (const child of treeNode.children) emitNode(child, depth + 1, parentStack);
		parentStack.pop();
	} else if (node.kind === "cube") {
		const boneOrigin = parent.uuid ? (nodes.get(parent.uuid)?.origin || [0, 0, 0]) : [0, 0, 0];
		lines.push(`${pad}${parent.varName}.addOrReplaceChild("${node.name}", ${cubeBuilderExpr(node, boneOrigin)}, PartPose.ZERO);`);
	}
}
for (const root of outlinerTree) emitNode(root, 0, [{ varName: "root", uuid: null }]);

const modelFile = `package ${pkg};

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class ${cls}Model extends EntityModel<${cls}RenderState> {
	public ${cls}Model(final ModelPart root) {
		super(root.getChild("root"));
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
${lines.map((l) => indent + l).join("\n")}
		return LayerDefinition.create(mesh, ${TEX_W}, ${TEX_H});
	}

	@Override
	public void setupAnim(final ${cls}RenderState state) {
		super.setupAnim(state);
	}
}
`;

// ---- 生成动画代码 ----
function buildAnimation(anim) {
	const name = anim.name.replace(/^animation\./, "").toUpperCase();
	const head = `	public static final AnimationDefinition ${name} = AnimationDefinition.Builder.withLength(${f2(anim.length)})${anim.loop === "loop" ? "\n\t\t.looping()" : ""}`;
	const adds = [];
	for (const an of Object.values(anim.animators || {})) {
		const byChannel = {};
		for (const kf of an.keyframes || []) (byChannel[kf.channel] ||= []).push(kf);
		for (const [channel, kfs] of Object.entries(byChannel)) {
			const target = channel === "position" ? "AnimationChannel.Targets.POSITION" : channel === "scale" ? "AnimationChannel.Targets.SCALE" : "AnimationChannel.Targets.ROTATION";
			const kfLines = kfs.map((kf) => {
				const dp = kf.data_points || [];
				const x = Number(dp[0]?.x ?? 0), y = Number(dp[0]?.y ?? 0), z = Number(dp[0]?.z ?? 0);
				const vec = channel === "position" ? `KeyframeAnimations.posVec(${f2(x)}, ${f2(y)}, ${f2(z)})`
					: channel === "scale" ? `KeyframeAnimations.scaleVec(${x.toFixed(4)}, ${y.toFixed(4)}, ${z.toFixed(4)})`
					: `KeyframeAnimations.degreeVec(${f2(x)}, ${f2(y)}, ${f2(z)})`;
				const interp = kf.interpolation === "catmullrom" || kf.interpolation === "smooth" ? "CATMULLROM" : "LINEAR";
				return `new Keyframe(${f2(kf.time)}, ${vec}, AnimationChannel.Interpolations.${interp})`;
			});
			adds.push(`\t\t.addAnimation(\n\t\t\t"${an.name}",\n\t\t\tnew AnimationChannel(\n\t\t\t\t${target},\n${kfLines.map((k) => "\t\t\t\t" + k).join(",\n")}\n\t\t\t)\n\t\t)`);
		}
	}
	return [head, ...adds, "\t\t.build();"].join("\n");
}

const animFile = `package ${pkg};

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

public class ${cls}Animations {
${(bb.animations || []).map(buildAnimation).join("\n\n")}
}
`;

fs.mkdirSync(outDir, { recursive: true });
const modelPath = path.join(outDir, `${cls}Model.java`);
const animPath = path.join(outDir, `${cls}Animations.java`);
fs.writeFileSync(modelPath, modelFile);
fs.writeFileSync(animPath, animFile);
console.log(`wrote ${modelPath}`);
console.log(`wrote ${animPath}`);
