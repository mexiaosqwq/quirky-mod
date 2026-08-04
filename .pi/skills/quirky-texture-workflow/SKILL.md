---
name: quirky-texture-workflow
description: Use when creating, redrawing, reviewing, previewing, validating, or publishing PNG assets for the Quirky Minecraft mod.
---

# Quirky Texture Workflow

## Overview

Every Quirky PNG starts from persistent structured source, renders into a build-only candidate, receives machine and visual review, and reaches formal resources only after explicit human approval. Pixel facts, visual judgment, and approval are separate authorities.

## Route Selection

Check the active model in `PI_PROVIDER`/`PI_MODEL` and confirm its `images` column with `pi --list-models`.

- **Route A — active model can see images:** the active model reviews candidates and proposes source changes; always run a fresh `texture-visual-auditor` session afterward.
- **Route B — active model is text-only:** run the read-only `texture-visual-designer`; the active orchestrator applies accepted proposals to `source.json`; then run a separate fresh read-only `texture-visual-auditor` session.

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

3. Inspect `native.png`, `nearest-16x.png`, checker, light, dark, and inventory-context previews. Treat `pixel-facts.json` as authoritative for dimensions, RGBA, alpha, palette, and bounds.
4. The orchestrator invokes the route-specific designer and fresh auditor, then saves each raw response under the asset build report directory. `check-report` only validates an already-saved response; it does not invoke an agent or create a report:

```bash
python -m tools.texture_pipeline check-report <report.json> --role designer --width <w> --height <h>
python -m tools.texture_pipeline check-report <report.json> --role auditor --width <w> --height <h>
```

5. Distinguish failures: `validate`/`render` failures mean the orchestrator fixes `asset.json` or `source.json` and reruns the machine command; do not send them to a visual role. A `check-report` failure means send only report-validator errors to the same visual role and retry it once. A second invalid report stops for human review.
6. Present candidate comparisons and reports to the user only after auditor status `pass_visual`. The user is the only final approver; `pass_visual` is not publication permission.
7. Only after explicit approval may a later task copy the selected candidate PNG from `build/` to the exact `.png` path declared by `asset.json`. Visual agents never edit source or copy files. For a new item, also use `quirky-new-item-checklist`.

## Authority and Conflict Rules

| Question | Authority |
|---|---|
| Exact pixel/color/alpha exists? | Machine report |
| Silhouette/material/readability works? | Visual review |
| Candidate becomes formal? | User |

When visual and machine reports conflict, preserve both statements, use machine data for pixel facts, and investigate the perceptual issue. Never silently choose the model's claim.

## Failure Stops

- Missing/unreadable image: visual status is blocked; do not infer from filename.
- Machine gate failure: candidate remains build-only.
- Invalid visual JSON twice: stop for human review.
- No explicit user approval: do not publish.

Do not run Gradle for skill, agent, documentation, or standalone tooling changes. Run the project `gradle build` only in a later task that actually changes formal Minecraft resources or mod code.

## Common Mistakes

- Storing editable source under `build/`, or storing candidate PNGs under persistent source.
- Letting a read-only visual agent edit `source.json` or publish files.
- Treating `check-report` as an agent invocation rather than a validator.
- Letting an image-capable main model approve its own candidate.
- Treating visual prose as exact pixel evidence.
- Reusing one conversation for design and audit.
- Overwriting a formal PNG before preview approval.
