# Quirky PNG Asset Pipeline Design

**Date:** 2026-08-04  
**Status:** Approved for implementation  
**Scope:** Project tooling, Pi skill, and dedicated visual agents; no formal texture replacement in this change

## 1. Purpose

Quirky needs a repeatable PNG production workflow that works whether the active coding model can inspect images or is text-only. The workflow must preserve Minecraft pixel-art constraints, use a dedicated Claude Opus visual specialist when needed, distinguish machine-verifiable pixel facts from perceptual judgment, and require the user to approve every formal visual change.

This change replaces the current one-off pattern—hard-coded Python functions that write directly to formal assets—with a staged asset-package pipeline. Existing formal PNG files remain unchanged until a later, explicitly approved texture task publishes a candidate.

## 2. Goals

- Provide one canonical asset package format for Quirky PNG work.
- Render exact RGBA pixel assets from JSON without Pillow.
- Analyze PNG dimensions, alpha, palette, bounds, and margins deterministically.
- Generate previews under `build/texture-pipeline/` before any formal write.
- Support two model routes:
  - active model can inspect images;
  - active model is text-only and delegates visual work.
- Add isolated `texture-visual-designer` and `texture-visual-auditor` Pi agents backed by `gorouter/claude-opus-5-thinking`.
- Add a project skill that teaches the exact workflow, paths, gates, and failure handling.
- Keep the first implementation small, testable, and usable for 16×16 item/GUI/block textures.

## 3. Non-goals

- Do not redraw or replace an existing formal texture in this change.
- Do not add Stable Diffusion, ComfyUI, cloud image-generation adapters, Pillow, or a GUI.
- Do not build a generic cross-project art framework yet; implement Quirky-first boundaries that can be extracted later.
- Do not claim that visual-model output proves exact pixel facts.
- Do not make visual-model approval equivalent to user approval.
- Do not run the Minecraft Gradle build for skill/tooling-only edits; use focused Python and agent verification. A later task that publishes formal Minecraft resources must run `gradle build`.

## 4. Authorities

| Authority | Owns | Must not decide |
|---|---|---|
| Deterministic tooling | dimensions, RGBA values, palette size, alpha values, occupied bounds, output paths | style, meaning, visual appeal |
| Visual designer/auditor | silhouette, readability, material, composition, Minecraft-style fit | exact pixel truth, formal approval |
| User | semantic correctness, aesthetic selection, permission to publish | implementation-level statistics |

When a visual report conflicts with pixel data, the analyzer is authoritative for the pixel fact. The disagreement remains visible in the report and may still reveal a perceptual problem.

## 5. Model Routing

### Route A: active model supports image input

1. The active model reads the brief, candidate, and preview matrix.
2. The active model proposes structured source changes.
3. The local renderer creates a new candidate.
4. A fresh `texture-visual-auditor` session independently audits the candidate.
5. Machine checks validate all factual claims that can be computed.
6. The user approves or rejects the candidate.

### Route B: active model is text-only

1. The active model writes or modifies the brief and structured source.
2. The local renderer creates a candidate and machine report.
3. A fresh `texture-visual-designer` session reviews the candidate and returns structured proposals.
4. The active model applies accepted proposals to structured source.
5. A separate fresh `texture-visual-auditor` session performs the independent audit.
6. Machine checks validate computable claims.
7. The user approves or rejects the candidate.

Both routes use the same asset package, renderer, previewer, analyzer, audit contract, and user gate.

## 6. Repository Layout

```text
.pi/
├── agents/
│   ├── texture-visual-designer.md
│   └── texture-visual-auditor.md
└── skills/
    └── quirky-texture-workflow/
        └── SKILL.md

tools/
├── __init__.py
└── texture_pipeline/
    ├── __init__.py
    ├── __main__.py
    ├── asset.py
    ├── renderer.py
    ├── png_io.py
    ├── analyzer.py
    ├── preview.py
    ├── reports.py
    └── tests/
        ├── __init__.py
        ├── test_asset.py
        ├── test_renderer.py
        ├── test_analyzer.py
        ├── test_preview.py
        ├── test_cli.py
        ├── test_reports.py
        ├── test_agent_contracts.py
        └── test_skill_contract.py

build/texture-pipeline/<asset-id>/
├── candidates/<candidate-id>.png
├── previews/
│   ├── native.png
│   ├── nearest-16x.png
│   ├── checker-16x.png
│   ├── light-16x.png
│   ├── dark-16x.png
│   └── inventory-context.png
├── reports/
│   ├── pixel-facts.json
│   ├── visual-design.json
│   ├── visual-audit.json
│   └── validation.json
└── approval.json
```

Persistent asset packages will live under `tools/texture_pipeline/assets/` when a specific texture task is approved. This infrastructure change does not invent a new design for an existing asset merely to provide a sample; automated tests create temporary packages instead.

## 7. Asset Package Contract

Each asset package contains `asset.json` and `source.json`.

### `asset.json`

Required fields:

```json
{
  "schemaVersion": 1,
  "assetId": "quirky:item/example",
  "assetClass": "item_texture",
  "output": {
    "path": "src/main/resources/assets/quirky/textures/item/example.png",
    "width": 16,
    "height": 16,
    "colorMode": "rgba"
  },
  "styleProfile": "quirky_vanilla_item",
  "brief": {
    "subject": "Example subject",
    "gameplayMeaning": "What the asset communicates",
    "visualPriority": ["primary reading", "secondary reading"]
  },
  "mustHave": ["clear native-scale silhouette"],
  "mustNotHave": ["smooth antialiasing"],
  "qualityGates": {
    "maximumPaletteSize": 24,
    "allowPartialAlpha": false,
    "requireVisualAudit": true,
    "requireHumanApproval": true
  }
}
```

Validation rules:

- `schemaVersion` must equal `1`.
- `assetId` must start with `quirky:` and contain only lowercase resource-path characters.
- `assetClass` is initially one of `item_texture`, `block_texture`, `gui_sprite`, or `particle_texture`.
- Output paths must remain under `src/main/resources/assets/quirky/textures/` and end in `.png`.
- Width and height must be positive integers; the first vertical slice supports dimensions up to 256.
- Brief lists and gate fields are required; empty semantic briefs are rejected.

### `source.json`

```json
{
  "canvas": {
    "width": 16,
    "height": 16,
    "background": "transparent"
  },
  "palette": {
    "outline": "#38271FFF",
    "base": "#A8754DFF"
  },
  "layers": [
    {
      "id": "body",
      "operation": "rect",
      "color": "base",
      "x": 4,
      "y": 4,
      "width": 8,
      "height": 8
    }
  ]
}
```

Initial operations are intentionally limited to:

- `point`: `x`, `y`;
- `rect`: `x`, `y`, `width`, `height`;
- `line`: integer endpoints using Bresenham semantics;
- `ellipse`: pixel-center containment with `cx`, `cy`, `rx`, `ry`;
- `polygon`: integer vertices with pixel-center fill;
- `copy`: copy a rectangular source region to a destination;
- `mirror`: mirror the current canvas horizontally or vertically.

Every layer has a unique `id`; colors are palette names or `#RRGGBBAA`. Coordinates outside the canvas are validation errors rather than implicit clipping. Rendering is ordered and deterministic.

## 8. CLI Contract

Run from the repository root:

```bash
python -m tools.texture_pipeline validate <asset-package-dir>
python -m tools.texture_pipeline render <asset-package-dir> --candidate <candidate-id>
python -m tools.texture_pipeline analyze <png-path> --output <report.json>
python -m tools.texture_pipeline preview <asset-package-dir> --candidate <candidate-id>
python -m tools.texture_pipeline check-report <visual-report.json> --role auditor --width 16 --height 16
```

`render` writes only under `build/texture-pipeline/`; it never writes to the formal output path declared in `asset.json`.

A future `publish` command is deliberately omitted from the first vertical slice. Publishing remains a manual, separately approved task until the candidate and approval contracts have been exercised on real assets.

## 9. Preview Matrix

Each candidate produces:

- `native.png`: exact native-size RGBA image;
- `nearest-16x.png`: 16× nearest-neighbor enlargement with transparency preserved;
- `checker-16x.png`: enlargement over a checker background;
- `light-16x.png`: enlargement over a light neutral background;
- `dark-16x.png`: enlargement over a dark neutral background;
- `inventory-context.png`: native image centered on a 32×32 inventory-like neutral slot.

No bilinear, bicubic, or antialiased scaling is permitted for pixel-asset previews. The visual prompt names every file and explains that only the native image contains source pixels.

## 10. Machine Report

`pixel-facts.json` contains:

- path and SHA-256;
- width, height, and color mode;
- palette entries and counts;
- transparent, opaque, and partial-alpha pixel counts;
- occupied bounding box;
- blank margins on four sides;
- validation warnings and failures.

Quality gates fail when:

- dimensions differ from the manifest;
- partial alpha exists while disallowed;
- palette size exceeds the limit;
- no occupied pixel exists;
- visible content touches every edge, leaving no margin (full-bleed);

A candidate whose content touches any single edge receives a warning, not a failure, unless the asset class/profile explicitly permits it.

## 11. Visual Agent Contracts

Both agents are project-local, pinned to `gorouter/claude-opus-5-thinking`, use `thinking: high`, receive only the `read` built-in tool, do not inherit parent context, and cannot edit files.

The visual prompt names every preview file (`native.png`, `nearest-16x.png`, `checker-16x.png`, `light-16x.png`, `dark-16x.png`, `inventory-context.png`) and states that only `native.png` contains source pixels; all other previews are nearest-neighbor enlargements or composites.

Every prompt begins with:

```text
TASK_DOMAIN: MINECRAFT_PNG_ASSET_PIPELINE
PROJECT: QUIRKY
ROLE: VISUAL_DESIGNER | VISUAL_AUDITOR
ASSET_ID: <resource id>
ASSET_CLASS: <class>
OUTPUT_FORMAT: STRICT_JSON
FINAL_AUTHORITY: HUMAN
```

The designer returns observations and proposals, not approval. The auditor returns `pass_visual`, `changes_required`, `unknown`, or `blocked`; `pass_visual` means only that the visual audit found no blocking issue. Every finding separates visible fact, judgment, recommendation, region, and confidence.

Reports wrapped in Markdown, missing `image_loaded`, containing out-of-range regions, or claiming formal approval fail `check-report` and must be retried once. A second invalid response stops with a human-review requirement.

## 12. Skill Contract

The `quirky-texture-workflow` skill triggers whenever an agent creates, redraws, reviews, validates, previews, or publishes a Quirky PNG asset. It must teach:

- capability routing;
- canonical source/build/formal paths;
- exact CLI commands;
- preview-before-formal-write rule;
- designer/auditor separation;
- machine-fact versus visual-judgment authority;
- invalid-report retry and blocked behavior;
- user-only final approval;
- formal resource checklist and Gradle build only when a later task actually publishes resources.

Baseline test without the skill showed that an agent invented build-only source paths, invented a generic `vision` role, and allowed a visual model or image-capable main model to approve. The skill must directly prevent these failures.

## 13. Verification

Focused verification for this infrastructure change:

```bash
python -m unittest discover -s tools/texture_pipeline/tests -v
python -m tools.texture_pipeline --help
pi --list-models | grep '^gorouter[[:space:]]\+claude-opus-5-thinking'
```

Agent smoke tests:

1. `texture-visual-designer` reads a generated calibration PNG and returns a parseable designer report.
2. `texture-visual-auditor` independently reads the same PNG and returns a parseable audit report.
3. `check-report` accepts valid reports and rejects Markdown-wrapped, approval-claiming, or malformed reports.

No `gradle build` is required unless this change unexpectedly touches formal resources or mod code.

## 14. Acceptance Criteria

- All focused Python tests pass.
- Renderer output is byte-stable for identical inputs.
- Analyzer reports exact known fixture facts.
- Preview files exist at the documented paths and preserve nearest-neighbor pixels.
- Agents are read-only, model-pinned, and use isolated prompts.
- Both agent smoke tests prove actual image loading.
- Skill baseline omissions are addressed and a fresh agent can retrieve the exact workflow.
- Existing formal PNG files are unchanged.
- Existing one-off scripts remain untouched until a separate migration task proves parity.
