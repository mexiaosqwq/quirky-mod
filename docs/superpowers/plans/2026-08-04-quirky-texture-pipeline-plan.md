# Quirky PNG Asset Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a tested Quirky-first PNG asset package renderer, analyzer, previewer, strict visual-report validator, project skill, and two read-only Claude Opus visual agents without modifying formal textures.

**Architecture:** JSON asset packages are the persistent source of truth. A dependency-free Python package renders exact RGBA pixels into `build/texture-pipeline/`, uses ImageMagick only to decode arbitrary PNG files, generates deterministic previews and machine facts, and validates visual-agent JSON. Route A lets an image-capable parent design before an independent Opus audit; Route B delegates design and audit to separate Opus sessions for a text-only parent. Both routes end at the same machine gates and user-only approval.

**Tech Stack:** Python 3 standard library (`argparse`, `dataclasses`, `hashlib`, `json`, `struct`, `subprocess`, `unittest`, `zlib`), ImageMagick 7 `magick`, Pi custom agents/skills, JSON.

## Global Constraints

- Do not modify any file under `src/main/resources/assets/quirky/` in this change.
- Do not modify existing `tools/gen_batch_b_textures.py` or `tools/gen_batch_b_previews.py`; migration requires a separate parity task.
- Write persistent source under `tools/texture_pipeline/assets/` only when a later asset-specific brief is approved; tests use temporary packages.
- Write candidates, previews, and reports only under `build/texture-pipeline/`.
- Use Python standard library plus the already-installed ImageMagick; do not add Pillow or Gradle dependencies.
- Visual agents are read-only, pinned to `gorouter/claude-opus-5-thinking`, and never provide final approval.
- Every formal visual change in a later task must be previewed and explicitly approved by the user before publishing.
- This tooling-only plan uses focused Python and agent checks, not `gradle build`.

---

### Task 1: Asset-package validation

**Files:**
- Create: `tools/__init__.py`
- Create: `tools/texture_pipeline/__init__.py`
- Create: `tools/texture_pipeline/asset.py`
- Create: `tools/texture_pipeline/tests/__init__.py`
- Create: `tools/texture_pipeline/tests/test_asset.py`

**Interfaces:**
- Produces: `AssetError(ValueError)`.
- Produces: `OutputSpec`, `QualityGates`, `AssetSpec`, and `AssetPackage` frozen dataclasses.
- Produces: `load_asset_package(package_dir: Path) -> AssetPackage`.
- Produces: `asset_build_dir(build_root: Path, asset_id: str) -> Path`.
- Consumes: `asset.json` and `source.json` matching the approved design spec.

- [ ] **Step 1: Write failing manifest tests**

Create `test_asset.py` with temporary valid `asset.json`/`source.json` fixtures and assertions that:

```python
package = load_asset_package(package_dir)
self.assertEqual(package.spec.asset_id, "quirky:item/example")
self.assertEqual(package.spec.output.width, 16)
self.assertEqual(asset_build_dir(Path("build/texture-pipeline"), package.spec.asset_id),
                 Path("build/texture-pipeline/quirky/item/example"))
```

Add one test per rejection: unsupported schema version, uppercase/invalid resource ID, output outside `src/main/resources/assets/quirky/textures/`, mismatched canvas dimensions, duplicate layer IDs, unknown palette reference, coordinate outside canvas, empty brief, and unsupported operation.

- [ ] **Step 2: Verify RED**

Run:

```bash
cd /data/data/com.termux/files/home/minecraft/.worktrees/texture-pipeline
python -m unittest tools.texture_pipeline.tests.test_asset -v
```

Expected: import failure because `tools.texture_pipeline.asset` does not exist.

- [ ] **Step 3: Implement minimal package parsing**

Implement exact JSON type checks rather than coercion. Resolve `asset.json` and `source.json` relative to `package_dir`, but retain the declared formal output as a repository-relative `PurePosixPath`. Validate the operation-specific required integer fields:

```python
_OPERATION_FIELDS = {
    "point": ("x", "y"),
    "rect": ("x", "y", "width", "height"),
    "line": ("x1", "y1", "x2", "y2"),
    "ellipse": ("cx", "cy", "rx", "ry"),
    "polygon": ("points",),
    "copy": ("x", "y", "width", "height", "destX", "destY"),
    "mirror": ("axis",),
}
```

Validate all drawable bounds before rendering. For polygon points, require at least three `[x, y]` integer pairs inside the canvas. For `copy`, require source and destination rectangles inside the canvas. For `mirror`, allow only `horizontal` or `vertical`.

- [ ] **Step 4: Verify GREEN**

Run the same unittest command. Expected: all asset tests pass.

- [ ] **Step 5: Commit**

```bash
git add tools/__init__.py tools/texture_pipeline/__init__.py tools/texture_pipeline/asset.py tools/texture_pipeline/tests
git commit -m "feat: add texture asset package validation"
```

---

### Task 2: Deterministic renderer and PNG codec

**Files:**
- Create: `tools/texture_pipeline/png_io.py`
- Create: `tools/texture_pipeline/renderer.py`
- Create: `tools/texture_pipeline/tests/test_renderer.py`

**Interfaces:**
- Consumes: validated `AssetPackage.source`.
- Produces: `RGBA = tuple[int, int, int, int]` and `Pixels = list[list[RGBA]]`.
- Produces: `parse_color(value: str, palette: Mapping[str, str]) -> RGBA` for `transparent`, palette names, `#RRGGBB`, and `#RRGGBBAA`.
- Produces: `render_source(source: Mapping[str, object]) -> Pixels`.
- Produces: `write_rgba_png(path: Path, pixels: Pixels) -> None`.
- Produces: `read_rgba_png(path: Path) -> Pixels`, using `magick <path> -depth 8 rgba:-` after an exact width/height probe.

- [ ] **Step 1: Write failing render tests**

Test ordered alpha-replacing layers on a 5×5 canvas. Include focused tests for point, rect, all Bresenham line octants, ellipse pixel-center containment, concave polygon even-odd fill, overlapping layers, copy overlap snapshot semantics, horizontal mirror, vertical mirror, palette references, literal colors, and deterministic PNG bytes.

The byte-stability assertion writes the same pixels to two temporary files and compares:

```python
self.assertEqual(first.read_bytes(), second.read_bytes())
self.assertEqual(read_rgba_png(first), expected_pixels)
```

- [ ] **Step 2: Verify RED**

Run:

```bash
python -m unittest tools.texture_pipeline.tests.test_renderer -v
```

Expected: import failure because renderer and codec do not exist.

- [ ] **Step 3: Implement minimal PNG codec**

Move the existing proven chunk-writing pattern into `png_io.py`, generalized to arbitrary non-empty rectangular RGBA matrices. Use PNG color type 6, bit depth 8, filter 0, and zlib level 9. Create parent directories before writing.

For reads, call ImageMagick without a shell:

```python
identify = subprocess.run(
    ["magick", "identify", "-format", "%w %h", str(path)],
    check=True, capture_output=True, text=True,
)
raw = subprocess.run(
    ["magick", str(path), "-depth", "8", "rgba:-"],
    check=True, capture_output=True,
).stdout
```

Require exactly `width * height * 4` bytes.

- [ ] **Step 4: Implement minimal renderer**

Use a list-of-rows canvas. Polygon fill evaluates each pixel center `(x + 0.5, y + 0.5)` using even-odd crossings. Copy reads from a snapshot before writing. Mirror replaces the complete current canvas. Layers replace RGBA values; blending is not part of this pixel-art vertical slice.

- [ ] **Step 5: Verify GREEN**

Run the renderer tests. Expected: all pass with no warnings.

- [ ] **Step 6: Commit**

```bash
git add tools/texture_pipeline/png_io.py tools/texture_pipeline/renderer.py tools/texture_pipeline/tests/test_renderer.py
git commit -m "feat: add deterministic texture renderer"
```

---

### Task 3: Pixel analyzer and quality gates

**Files:**
- Create: `tools/texture_pipeline/analyzer.py`
- Create: `tools/texture_pipeline/tests/test_analyzer.py`

**Interfaces:**
- Consumes: `Pixels`, `AssetSpec`, and a candidate path.
- Produces: `analyze_pixels(pixels: Pixels, path: Path | None = None) -> dict[str, object]`.
- Produces: `validate_candidate(spec: AssetSpec, facts: Mapping[str, object]) -> dict[str, object]` with `status`, `failures`, and `warnings`.
- Report keys: `sha256`, `width`, `height`, `colorMode`, `paletteSize`, `colors`, `transparentPixels`, `opaquePixels`, `partialAlphaPixels`, `occupiedBounds`, `blankMargins`.

- [ ] **Step 1: Write failing analyzer tests**

Use a 4×4 fixture with two transparent rows, one partial-alpha pixel, and two opaque colors. Assert exact counts, sorted color entries (`count` descending then RGBA ascending), bounds, margins, and SHA-256. Add gate tests for wrong dimensions, excess palette, forbidden partial alpha, empty image, and edge-touch warning.

- [ ] **Step 2: Verify RED**

Run:

```bash
python -m unittest tools.texture_pipeline.tests.test_analyzer -v
```

Expected: import failure because analyzer does not exist.

- [ ] **Step 3: Implement analyzer and gates**

Treat alpha `0` as transparent, `255` as opaque, and `1..254` as partial. Occupied bounds include any pixel with alpha greater than zero. Colors include complete `#RRGGBBAA` values and counts. Compute file hash only when a path is supplied.

- [ ] **Step 4: Verify GREEN**

Run analyzer tests. Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add tools/texture_pipeline/analyzer.py tools/texture_pipeline/tests/test_analyzer.py
git commit -m "feat: add texture pixel quality gates"
```

---

### Task 4: Preview generation and CLI vertical slice

**Files:**
- Create: `tools/texture_pipeline/preview.py`
- Create: `tools/texture_pipeline/__main__.py`
- Create: `tools/texture_pipeline/tests/test_preview.py`
- Create: `tools/texture_pipeline/tests/test_cli.py`

**Interfaces:**
- Consumes: `AssetPackage`, rendered candidate, and candidate ID.
- Produces: `scale_nearest(pixels: Pixels, factor: int) -> Pixels`.
- Produces: `composite_background(pixels: Pixels, background: RGBA) -> Pixels` using straight-alpha integer composition.
- Produces: `generate_preview_set(candidate: Path, output_dir: Path) -> dict[str, Path]`.
- CLI subcommands: `validate`, `render`, `analyze`, and `preview`.

- [ ] **Step 1: Write failing preview tests**

Assert that 2× nearest scaling creates exact 2×2 pixel blocks, alpha composition yields known RGBA values, checker squares alternate, and inventory context is exactly 32×32 with the native image centered.

- [ ] **Step 2: Write failing CLI integration test**

Create a temporary asset package and repository-like temporary root. Run:

```python
result = subprocess.run(
    [sys.executable, "-m", "tools.texture_pipeline", "render",
     str(package_dir), "--candidate", "v01", "--build-root", str(build_root)],
    cwd=repository_root, capture_output=True, text=True,
)
```

Assert exit 0; candidate, previews, `pixel-facts.json`, and `validation.json` exist; no file exists at the formal output path. Add invalid-package test expecting exit 2 and a concise `ERROR:` line.

- [ ] **Step 3: Verify RED**

Run:

```bash
python -m unittest tools.texture_pipeline.tests.test_preview tools.texture_pipeline.tests.test_cli -v
```

Expected: import/module failure.

- [ ] **Step 4: Implement preview set**

Generate the six approved files. Native is a byte-for-byte re-encode of the decoded pixels. All enlargement uses `scale_nearest`; backgrounds are applied after enlargement. Inventory context uses a fixed neutral slot palette documented in constants.

- [ ] **Step 5: Implement CLI**

`render` performs validation, render, write, analyze, gate validation, report writes, and preview generation in that order. It rejects candidate IDs outside `[a-z0-9][a-z0-9._-]*`. JSON report writes use `indent=2`, sorted keys, and a trailing newline.

- [ ] **Step 6: Verify GREEN and full Python suite**

Run:

```bash
python -m unittest discover -s tools/texture_pipeline/tests -v
python -m tools.texture_pipeline --help
```

Expected: all tests pass and help lists the four subcommands.

- [ ] **Step 7: Commit**

```bash
git add tools/texture_pipeline/preview.py tools/texture_pipeline/__main__.py tools/texture_pipeline/tests
git commit -m "feat: add texture previews and CLI"
```

---

### Task 5: Strict visual-report validator

**Files:**
- Create: `tools/texture_pipeline/reports.py`
- Create: `tools/texture_pipeline/tests/test_reports.py`
- Modify: `tools/texture_pipeline/__main__.py`
- Modify: `docs/superpowers/specs/2026-08-04-quirky-texture-pipeline-design.md`

**Interfaces:**
- Produces: `ReportError(ValueError)`.
- Produces: `validate_visual_report(raw: str, role: str, width: int, height: int) -> dict[str, object]`.
- Adds CLI: `check-report <path> --role designer|auditor --width N --height N`.

- [ ] **Step 1: Write failing report tests**

Valid designer reports require `taskDomain`, `project`, `role`, `assetId`, `imageLoaded`, `observations`, `proposals`, `unknowns`, and `humanReviewRequired: true`. Valid auditor reports require those identity fields plus `status`, `findings`, `unknowns`, and `humanReviewRequired: true`.

Reject:

- Markdown fences or any non-whitespace outside the JSON object;
- `imageLoaded` other than `true`;
- role mismatch;
- auditor status outside `pass_visual`, `changes_required`, `unknown`, `blocked`;
- `approved`, `finalApproved`, or equivalent approval claims anywhere in keys;
- finding regions outside canvas bounds;
- confidence outside `0..1`;
- findings missing `visibleFact`, `judgment`, `recommendation`, `region`, or `confidence`.

- [ ] **Step 2: Verify RED**

Run the report test module. Expected: import failure.

- [ ] **Step 3: Implement exact JSON validation**

Use `json.JSONDecoder().raw_decode` and reject any trailing non-whitespace. Recursively reject key names containing normalized `approve` except the required `humanReviewRequired`. Keep the accepted schema deliberately narrow.

- [ ] **Step 4: Add CLI and update design file list**

Add `reports.py` and `test_reports.py` to the design layout. `check-report` prints canonical JSON on success and `ERROR:` to stderr with exit 2 on failure.

- [ ] **Step 5: Verify GREEN**

Run the report tests and complete Python suite.

- [ ] **Step 6: Commit**

```bash
git add tools/texture_pipeline/reports.py tools/texture_pipeline/__main__.py tools/texture_pipeline/tests/test_reports.py
git add -f docs/superpowers/specs/2026-08-04-quirky-texture-pipeline-design.md
git commit -m "feat: validate visual texture reports"
```

---

### Task 6: Dedicated visual agents

**Files:**
- Create: `.pi/agents/texture-visual-designer.md`
- Create: `.pi/agents/texture-visual-auditor.md`
- Create: `tools/texture_pipeline/tests/test_agent_contracts.py`

**Interfaces:**
- Produces agent types `texture-visual-designer` and `texture-visual-auditor`.
- Both use `model: gorouter/claude-opus-5-thinking`, `thinking: high`, `tools: read`, `prompt_mode: replace`, `inherit_context: false`, `isolated: true`, and `output_transcript: false`.
- Both output raw JSON matching `reports.py`.

- [ ] **Step 1: Write failing static contract tests**

Read both Markdown files and assert exact required frontmatter values, required task-domain label, `FINAL_AUTHORITY: HUMAN`, explicit no-write/no-approval language, and role-specific schema keys. Assert auditor prompt requires a fresh session and excludes designer transcript/self-evaluation.

- [ ] **Step 2: Verify RED**

Run:

```bash
python -m unittest tools.texture_pipeline.tests.test_agent_contracts -v
```

Expected: missing agent files.

- [ ] **Step 3: Write minimal designer agent**

The prompt identifies the task as Quirky Minecraft PNG visual design, requires actual image loading through `read`, separates visible observations from proposals, treats machine facts as factual inputs, and returns raw JSON only. It cannot declare visual or final approval.

- [ ] **Step 4: Write minimal auditor agent**

The prompt requires independent review of native and enlarged previews against `asset.json`, returns region/evidence/confidence findings, allows `pass_visual` only as a non-final visual verdict, and returns `unknown` when evidence is insufficient.

- [ ] **Step 5: Verify static contracts**

Run agent contract tests. Expected: pass.

- [ ] **Step 6: Run runtime image smoke tests**

Generate a calibration fixture via the CLI. Spawn each agent in a fresh foreground session against the candidate and preview paths. Save each raw result to the build report path and run `check-report`. If a report is invalid, inject one correction containing only the validator errors and retry once. A second invalid result is a test failure requiring prompt revision.

- [ ] **Step 7: Commit**

```bash
git add .pi/agents/texture-visual-designer.md .pi/agents/texture-visual-auditor.md tools/texture_pipeline/tests/test_agent_contracts.py
git commit -m "feat: add dedicated texture visual agents"
```

---

### Task 7: Project workflow skill using RED-GREEN-REFACTOR

**Files:**
- Create: `.pi/skills/quirky-texture-workflow/SKILL.md`
- Create: `tools/texture_pipeline/tests/test_skill_contract.py`

**Interfaces:**
- Produces project skill `quirky-texture-workflow`.
- Trigger: creating, redrawing, reviewing, previewing, validating, or publishing Quirky Minecraft PNG assets.
- Consumes CLI and agent types from Tasks 4–6.

- [ ] **Step 1: Record the baseline failures**

Use the already-run isolated baseline scenario. Record these observed failures in the test rationale comments:

- persistent source was proposed under `build/previews/`;
- role name was invented as generic `vision`;
- an image-capable main model or visual model was allowed to approve;
- exact candidate/report paths and strict report validation were unknown.

- [ ] **Step 2: Write failing skill contract test**

Assert the skill does not exist, then define required discovery/frontmatter and workflow content checks: description begins `Use when`, mentions Quirky PNG triggers without summarizing the process, includes Route A/B decision, exact source/build/formal paths, exact CLI names, exact agent names, user-only approval, one-retry report handling, and no Gradle requirement until formal resources change.

- [ ] **Step 3: Verify RED**

Run:

```bash
python -m unittest tools.texture_pipeline.tests.test_skill_contract -v
```

Expected: missing skill file.

- [ ] **Step 4: Write the minimal skill**

Keep `SKILL.md` under 500 words where practical. Use a small decision flow only for image-capable versus text-only routing, a numbered procedure, a compact authority table, failure handling, command quick reference, and common mistakes derived from the baseline.

- [ ] **Step 5: Verify static GREEN**

Run skill contract test. Expected: pass.

- [ ] **Step 6: Run fresh-agent application test with the skill**

Spawn a fresh text-only agent with `skills: quirky-texture-workflow` or explicitly instruct it to load the project skill. Re-run the exact baseline retrieval scenario. It passes only if it names both agents, keeps persistent source out of `build/`, treats model verdicts as non-final, uses the documented preview/report paths, and requires user approval.

- [ ] **Step 7: Refactor only observed loopholes**

If the fresh agent invents or skips a step, add the smallest instruction that closes that exact gap, then rerun the same scenario. Do not add speculative sections.

- [ ] **Step 8: Commit**

```bash
git add .pi/skills/quirky-texture-workflow/SKILL.md tools/texture_pipeline/tests/test_skill_contract.py
git commit -m "docs: add Quirky texture workflow skill"
```

---

### Task 8: End-to-end verification and documentation review

**Files:**
- Modify: `docs/superpowers/specs/2026-08-04-quirky-texture-pipeline-design.md`
- Modify: `docs/superpowers/plans/2026-08-04-quirky-texture-pipeline-plan.md`

**Interfaces:**
- Verifies all prior task interfaces together.

- [ ] **Step 1: Run complete focused verification**

```bash
python -m unittest discover -s tools/texture_pipeline/tests -v
python -m tools.texture_pipeline --help
pi --list-models | grep '^gorouter[[:space:]]\+claude-opus-5-thinking'
git diff --check
git status --short
```

Expected: tests pass, CLI help succeeds, model shows `images=yes`, no whitespace errors, and only planned files differ.

- [ ] **Step 2: Run an end-to-end temporary package**

Create a package under the test temporary directory, render candidate `v01`, validate all six previews and both machine reports, run designer and auditor smoke tests, and validate their reports. Confirm no formal resource path was created or modified.

- [ ] **Step 3: Self-review against the design spec**

Check every acceptance criterion and update only inaccurate documentation. Search for placeholder markers and stale names:

```bash
python - <<'PY'
from pathlib import Path
markers = ["T" + "BD", "TO" + "DO", "implement " + "later"]
roots = [Path("docs/superpowers"), Path(".pi"), Path("tools/texture_pipeline")]
for root in roots:
    for path in root.rglob("*"):
        if path.is_file():
            text = path.read_text(errors="ignore")
            for marker in markers:
                if marker in text:
                    raise SystemExit(f"placeholder {marker!r} in {path}")
PY
rg -n 'texture-visual-(designer|auditor)|check-report|build/texture-pipeline' \
  docs/superpowers/specs/2026-08-04-quirky-texture-pipeline-design.md \
  docs/superpowers/plans/2026-08-04-quirky-texture-pipeline-plan.md \
  .pi tools/texture_pipeline
```

The marker scan must exit successfully.

- [ ] **Step 4: Request two-stage review**

Run specification-compliance review first, then code-quality/security review. Apply only findings verified against actual files and rerun focused verification.

- [ ] **Step 5: Final commit**

```bash
git add -f docs/superpowers/specs/2026-08-04-quirky-texture-pipeline-design.md docs/superpowers/plans/2026-08-04-quirky-texture-pipeline-plan.md
git commit -m "docs: finalize texture pipeline design"
```
