"""Command-line entry point for the Quirky texture pipeline."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

from .analyzer import analyze_pixels, validate_candidate
from .asset import AssetError, asset_build_dir, load_asset_package
from .png_io import PngError, read_rgba_png, write_rgba_png
from .preview import generate_preview_set
from .renderer import RenderError, render_source
from .reports import ReportError, validate_visual_report


_CANDIDATE_ID = re.compile(r"^[a-z0-9][a-z0-9._-]*$")


def _write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def _print_json(value: Any) -> None:
    print(json.dumps(value, indent=2, sort_keys=True))


def _candidate_path(package_dir: Path, build_root: Path, candidate_id: str) -> tuple[Path, Path]:
    if not _CANDIDATE_ID.fullmatch(candidate_id):
        raise AssetError("candidate id must contain only lowercase letters, digits, dot, underscore, or hyphen")
    package = load_asset_package(package_dir)
    output_dir = asset_build_dir(build_root, package.spec.asset_id)
    return output_dir / "candidates" / f"{candidate_id}.png", output_dir


def _validate_command(arguments: argparse.Namespace) -> None:
    package = load_asset_package(arguments.package)
    _print_json({"assetId": package.spec.asset_id, "status": "pass"})


def _render_command(arguments: argparse.Namespace) -> None:
    package = load_asset_package(arguments.package)
    if not _CANDIDATE_ID.fullmatch(arguments.candidate):
        raise AssetError("candidate id must contain only lowercase letters, digits, dot, underscore, or hyphen")
    output_dir = asset_build_dir(arguments.build_root, package.spec.asset_id)
    candidate = output_dir / "candidates" / f"{arguments.candidate}.png"
    pixels = render_source(package.source)
    write_rgba_png(candidate, pixels)
    facts = analyze_pixels(pixels, candidate)
    validation = validate_candidate(package.spec, facts)
    _write_json(output_dir / "reports" / "pixel-facts.json", facts)
    _write_json(output_dir / "reports" / "validation.json", validation)
    previews = generate_preview_set(candidate, output_dir / "previews")
    _print_json(
        {
            "assetId": package.spec.asset_id,
            "candidate": str(candidate),
            "previews": {name: str(path) for name, path in previews.items()},
            "status": validation["status"],
        }
    )


def _analyze_command(arguments: argparse.Namespace) -> None:
    pixels = read_rgba_png(arguments.png)
    facts = analyze_pixels(pixels, arguments.png)
    _write_json(arguments.output, facts)
    _print_json(facts)


def _preview_command(arguments: argparse.Namespace) -> None:
    candidate, output_dir = _candidate_path(arguments.package, arguments.build_root, arguments.candidate)
    if not candidate.is_file():
        raise AssetError(f"candidate does not exist: {candidate}")
    previews = generate_preview_set(candidate, output_dir / "previews")
    _print_json({"previews": {name: str(path) for name, path in previews.items()}, "status": "pass"})


def _check_report_command(arguments: argparse.Namespace) -> None:
    report = validate_visual_report(
        arguments.report.read_text(encoding="utf-8"),
        arguments.role,
        arguments.width,
        arguments.height,
    )
    _print_json(report)


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="python -m tools.texture_pipeline", description="Quirky deterministic PNG asset pipeline")
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate = subparsers.add_parser("validate", help="validate an asset package")
    validate.add_argument("package", type=Path)
    validate.set_defaults(handler=_validate_command)

    render = subparsers.add_parser("render", help="render a staged candidate, reports, and previews")
    render.add_argument("package", type=Path)
    render.add_argument("--candidate", required=True)
    render.add_argument("--build-root", type=Path, default=Path("build/texture-pipeline"))
    render.set_defaults(handler=_render_command)

    analyze = subparsers.add_parser("analyze", help="write exact pixel facts for a PNG")
    analyze.add_argument("png", type=Path)
    analyze.add_argument("--output", type=Path, required=True)
    analyze.set_defaults(handler=_analyze_command)

    preview = subparsers.add_parser("preview", help="regenerate previews for a staged candidate")
    preview.add_argument("package", type=Path)
    preview.add_argument("--candidate", required=True)
    preview.add_argument("--build-root", type=Path, default=Path("build/texture-pipeline"))
    preview.set_defaults(handler=_preview_command)

    check_report = subparsers.add_parser("check-report", help="validate a strict visual-agent JSON report")
    check_report.add_argument("report", type=Path)
    check_report.add_argument("--role", choices=("designer", "auditor"), required=True)
    check_report.add_argument("--width", type=int, required=True)
    check_report.add_argument("--height", type=int, required=True)
    check_report.set_defaults(handler=_check_report_command)
    return parser


def main(argv: list[str] | None = None) -> int:
    try:
        arguments = _parser().parse_args(argv)
        arguments.handler(arguments)
        return 0
    except (AssetError, PngError, RenderError, ReportError, OSError, ValueError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
