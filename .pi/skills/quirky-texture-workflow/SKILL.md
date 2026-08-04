---
name: quirky-texture-workflow
description: Use when creating, redrawing, reviewing, previewing, validating, or publishing PNG assets for the Quirky Minecraft mod.
---

# Quirky Texture Workflow

## Overview

Every Quirky PNG starts from persistent structured source, renders into a build-only candidate, receives machine and visual review, and reaches formal resources only after explicit human approval. Pixel facts, visual judgment, and approval are separate authorities.

## Route Selection

Check the active model in `PI_PROVIDER`/`PI_MODEL` and confirm its `images` column with `pi --list-models`.

- **Route A — active model can see images:** the active model designs from the previews and writes the complete `editPlan`; then one fresh `texture-visual-auditor` session (1 Opus call, ~$0.2).
- **Route B — active model is text-only:** one `texture-visual-designer` session returns the complete `editPlan` (~$0.2), apply it, then one fresh `texture-visual-auditor` session (~$0.2).

Budget cap: **≤2 Opus calls per asset** (~$0.4). Never start a second auditor round; the orchestrator never judges aesthetics from ASCII/pixel dumps — `verdict` comes from the visual role.

Never send the designer transcript or self-evaluation to the auditor.

## Canonical Paths

| Content | Path |
|---|---|
| Persistent asset source | `tools/texture_pipeline/assets/<class>/<id>/` |
| Candidate, previews, reports | `build/texture-pipeline/<namespace>/<path>/` |
| Formal Minecraft PNG | `src/main/resources/assets/quirky/textures/...` |

`build/` is disposable output, never persistent source. Do not invent round-N directories or Markdown report names. The fixed asset build layout is:

```text
candidates/<candidate-id>.png
previews/{native,nearest-16x,checker-16x,light-16x,dark-16x,inventory-context}.png
reports/{pixel-facts,validation,visual-design,visual-audit}.json
approval.json
```

The exact formal `.png` path is `asset.json` → `output.path`. Do not write it while designing.

## Procedure

1. Create or update `asset.json` and `source.json` in the persistent asset package. Briefs must state subject, gameplay meaning, visual priorities, required details, forbidden readings, and quality gates.
2. Validate and render:

```bash
python -m tools.texture_pipeline validate <asset-package>
python -m tools.texture_pipeline render <asset-package> --candidate <id>
```

3. Inspect machine reports. `pixel-facts.json` is authoritative for dimensions, RGBA, alpha, palette, and bounds. The visual role's `verdict` carries the overall-read judgment.
4. The orchestrator invokes the route-specific single-call design pass, saves the response, validates with `check-report`, applies, and renders:

```bash
python -m tools.texture_pipeline check-report <report.json> --role designer --width <w> --height <h>
python -m tools.texture_pipeline apply <asset-package> --plan <report.json> --candidate <id> --build-root build/texture-pipeline
```

`apply` validates the plan, applies layer `add`/`update`/`delete` operations to `source.json`, re-validates the package (fails without touching the file), and renders a candidate. On a contract error, fix the plan or `source.json` and retry, do not hand-edit pixel geometry. Then run one fresh `texture-visual-auditor` session.

5. Distinguish failures: `validate`/`render`/`apply` failures mean the orchestrator fixes `asset.json`, `source.json`, or the edit plan and reruns the machine command; do not send them to a visual role. A `check-report` failure means send only report-validator errors to the same visual role and retry it once. A second invalid report stops for human review.

5b. Convergence: the auditor's single call is final. If `status` is `pass_visual` or all `blocking` findings have fixes in its `editPlan`, apply and stop (skip `apply` when `operations` is empty). `major`/`minor` findings without `blocking` go to the human as optional notes. If unresolved `blocking` items remain after one apply, stop and present evidence — do not loop visual calls.
6. Present candidate comparisons and reports only after auditor status `pass_visual` or the convergence stop above. The user is the only final approver; `pass_visual` is not publication permission.
7. Only after explicit approval may a later task copy the selected candidate PNG from `build/` to the exact `.png` path declared by `asset.json`. Visual agents never edit source or copy files. For a new item, also use `quirky-new-item-checklist`.

## Authority and Conflict Rules

| Question | Authority |
|---|---|
| Exact pixel/color/alpha exists? | Machine report |
| Silhouette/material/readability works? | Visual review |
| Candidate becomes formal? | User |

When visual and machine reports conflict, preserve both, use machine data for pixel facts, and investigate the perceptual issue. Never silently choose the model's claim.

## Failure Stops

- Missing/unreadable image: visual status is blocked; do not infer from filename.
- Machine gate failure: candidate remains build-only.
- Invalid visual JSON twice: stop for human review.
- No explicit user approval: do not publish.

Do not run Gradle for skill/agent/doc/tooling changes; run `gradle build` only when changing formal resources or mod code.

## Common Mistakes

- Storing editable source under `build/`, or storing candidate PNGs under persistent source.
- Letting a read-only visual agent edit `source.json` or publish files.
- Treating `check-report` as an agent invocation, not a validator.
- Letting an image-capable main model approve its own candidate.
- Treating visual prose as exact pixel evidence.
- Reusing one conversation for design and audit.
- Overwriting a formal PNG before preview approval.
