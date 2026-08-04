"""Load and validate Quirky texture asset packages."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any, Mapping


_ASSET_ID = re.compile(r"^quirky:[a-z0-9._-]+(?:/[a-z0-9._-]+)*$")
_LAYER_ID = re.compile(r"^[a-z][a-z0-9_]*$")
_HEX_COLOR = re.compile(r"^#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?$")
_ASSET_CLASSES = {"item_texture", "block_texture", "gui_sprite", "particle_texture"}
_DRAW_OPERATIONS = {"point", "rect", "line", "ellipse", "polygon"}
_OPERATIONS = _DRAW_OPERATIONS | {"copy", "mirror"}
_OUTPUT_PREFIX = PurePosixPath("src/main/resources/assets/quirky/textures")


def _valid_asset_id(asset_id: str) -> bool:
    """Return whether an asset id is a valid quirky resource id with safe path segments."""
    if not _ASSET_ID.fullmatch(asset_id):
        return False
    _, path = asset_id.split(":", 1)
    return all(segment not in {"", ".", ".."} and ".." not in segment for segment in path.split("/"))


class AssetError(ValueError):
    """Raised when an asset package violates the project contract."""


@dataclass(frozen=True)
class OutputSpec:
    path: PurePosixPath
    width: int
    height: int
    color_mode: str


@dataclass(frozen=True)
class QualityGates:
    maximum_palette_size: int
    allow_partial_alpha: bool
    require_visual_audit: bool
    require_human_approval: bool


@dataclass(frozen=True)
class AssetSpec:
    asset_id: str
    asset_class: str
    output: OutputSpec
    style_profile: str
    brief: Mapping[str, Any]
    must_have: tuple[str, ...]
    must_not_have: tuple[str, ...]
    quality_gates: QualityGates


@dataclass(frozen=True)
class AssetPackage:
    package_dir: Path
    spec: AssetSpec
    source: Mapping[str, Any]


def _load_object(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise AssetError(f"missing {path.name}") from exc
    except json.JSONDecodeError as exc:
        raise AssetError(f"invalid JSON in {path.name}: {exc.msg}") from exc
    if not isinstance(value, dict):
        raise AssetError(f"{path.name} must contain a JSON object")
    return value


def _required(mapping: Mapping[str, Any], key: str, expected: type, label: str) -> Any:
    if key not in mapping:
        raise AssetError(f"missing {label}.{key}")
    value = mapping[key]
    if expected is int:
        valid = type(value) is int
    elif expected is bool:
        valid = type(value) is bool
    else:
        valid = isinstance(value, expected)
    if not valid:
        raise AssetError(f"{label}.{key} must be {expected.__name__}")
    return value


def _nonempty_string(mapping: Mapping[str, Any], key: str, label: str) -> str:
    value = _required(mapping, key, str, label)
    if not value.strip():
        raise AssetError(f"{label}.{key} must not be empty")
    return value


def _string_list(mapping: Mapping[str, Any], key: str, label: str, *, require_item: bool) -> tuple[str, ...]:
    value = _required(mapping, key, list, label)
    if require_item and not value:
        raise AssetError(f"{label}.{key} must not be empty")
    if any(not isinstance(item, str) or not item.strip() for item in value):
        raise AssetError(f"{label}.{key} must contain non-empty strings")
    return tuple(value)


def _positive_int(mapping: Mapping[str, Any], key: str, label: str, *, maximum: int | None = None) -> int:
    value = _required(mapping, key, int, label)
    if value <= 0 or (maximum is not None and value > maximum):
        suffix = f" and at most {maximum}" if maximum is not None else ""
        raise AssetError(f"{label}.{key} must be positive{suffix}")
    return value


def _coordinate(mapping: Mapping[str, Any], key: str, label: str) -> int:
    return _required(mapping, key, int, label)


def _validate_color(value: Any, palette: Mapping[str, str], label: str) -> None:
    if not isinstance(value, str):
        raise AssetError(f"{label} must be a color string")
    if value == "transparent" or _HEX_COLOR.fullmatch(value):
        return
    if value not in palette:
        raise AssetError(f"{label} references unknown palette color {value!r}")


def _inside_point(x: int, y: int, width: int, height: int) -> bool:
    return 0 <= x < width and 0 <= y < height


def _inside_rect(x: int, y: int, rect_width: int, rect_height: int, width: int, height: int) -> bool:
    return rect_width > 0 and rect_height > 0 and x >= 0 and y >= 0 and x + rect_width <= width and y + rect_height <= height


def _validate_layer(layer: Any, index: int, palette: Mapping[str, str], width: int, height: int) -> str:
    label = f"source.layers[{index}]"
    if not isinstance(layer, dict):
        raise AssetError(f"{label} must be an object")
    layer_id = _nonempty_string(layer, "id", label)
    if not _LAYER_ID.fullmatch(layer_id):
        raise AssetError(f"{label}.id must use lower_snake_case (letters, digits, underscores only)")
    operation = _nonempty_string(layer, "operation", label)
    if operation not in _OPERATIONS:
        raise AssetError(f"{label}.operation {operation!r} is unsupported")
    if operation in _DRAW_OPERATIONS:
        _validate_color(_required(layer, "color", str, label), palette, f"{label}.color")

    if operation == "point":
        x = _coordinate(layer, "x", label)
        y = _coordinate(layer, "y", label)
        if not _inside_point(x, y, width, height):
            raise AssetError(f"{label} point is outside canvas bounds")
    elif operation == "rect":
        x = _coordinate(layer, "x", label)
        y = _coordinate(layer, "y", label)
        rect_width = _positive_int(layer, "width", label)
        rect_height = _positive_int(layer, "height", label)
        if not _inside_rect(x, y, rect_width, rect_height, width, height):
            raise AssetError(f"{label} rectangle is outside canvas bounds")
    elif operation == "line":
        points = (
            (_coordinate(layer, "x1", label), _coordinate(layer, "y1", label)),
            (_coordinate(layer, "x2", label), _coordinate(layer, "y2", label)),
        )
        if any(not _inside_point(x, y, width, height) for x, y in points):
            raise AssetError(f"{label} line is outside canvas bounds")
    elif operation == "ellipse":
        cx = _coordinate(layer, "cx", label)
        cy = _coordinate(layer, "cy", label)
        rx = _positive_int(layer, "rx", label)
        ry = _positive_int(layer, "ry", label)
        if cx - rx < 0 or cy - ry < 0 or cx + rx >= width or cy + ry >= height:
            raise AssetError(f"{label} ellipse is outside canvas bounds")
    elif operation == "polygon":
        points = _required(layer, "points", list, label)
        if len(points) < 3:
            raise AssetError(f"{label} polygon requires at least three points")
        for point in points:
            if not isinstance(point, list) or len(point) != 2 or any(type(value) is not int for value in point):
                raise AssetError(f"{label} polygon points must be integer [x, y] pairs")
            if not _inside_point(point[0], point[1], width, height):
                raise AssetError(f"{label} polygon is outside canvas bounds")
    elif operation == "copy":
        x = _coordinate(layer, "x", label)
        y = _coordinate(layer, "y", label)
        rect_width = _positive_int(layer, "width", label)
        rect_height = _positive_int(layer, "height", label)
        dest_x = _coordinate(layer, "dest_x", label)
        dest_y = _coordinate(layer, "dest_y", label)
        if not _inside_rect(x, y, rect_width, rect_height, width, height) or not _inside_rect(
            dest_x, dest_y, rect_width, rect_height, width, height
        ):
            raise AssetError(f"{label} copy source or destination is outside canvas bounds")
    elif operation == "mirror":
        axis = _nonempty_string(layer, "axis", label)
        if axis not in {"horizontal", "vertical"}:
            raise AssetError(f"{label}.mirror.axis must be horizontal or vertical")
    return layer_id


def _validate_source(source: Mapping[str, Any], output: OutputSpec) -> None:
    canvas = _required(source, "canvas", dict, "source")
    width = _positive_int(canvas, "width", "source.canvas", maximum=256)
    height = _positive_int(canvas, "height", "source.canvas", maximum=256)
    if (width, height) != (output.width, output.height):
        raise AssetError("source.canvas dimensions must match asset output")

    palette = _required(source, "palette", dict, "source")
    for name, value in palette.items():
        if not isinstance(name, str) or not _LAYER_ID.fullmatch(name):
            raise AssetError("source.palette names must use lower_snake_case")
        if not isinstance(value, str) or not _HEX_COLOR.fullmatch(value):
            raise AssetError(f"source.palette.{name} must be #RRGGBB or #RRGGBBAA")
    _validate_color(_required(canvas, "background", str, "source.canvas"), palette, "source.canvas.background")

    layers = _required(source, "layers", list, "source")
    seen: set[str] = set()
    for index, layer in enumerate(layers):
        layer_id = _validate_layer(layer, index, palette, width, height)
        if layer_id in seen:
            raise AssetError(f"duplicate layer id {layer_id!r}")
        seen.add(layer_id)


def load_asset_package(package_dir: Path) -> AssetPackage:
    """Load and validate `asset.json` and `source.json` from a package directory."""
    package_dir = Path(package_dir)
    asset = _load_object(package_dir / "asset.json")
    source = _load_object(package_dir / "source.json")

    version = _required(asset, "schemaVersion", int, "asset")
    if version != 1:
        raise AssetError("asset.schemaVersion must equal 1")

    asset_id = _nonempty_string(asset, "assetId", "asset")
    if not _valid_asset_id(asset_id):
        raise AssetError("asset.assetId must be a lowercase quirky resource identifier")

    asset_class = _nonempty_string(asset, "assetClass", "asset")
    if asset_class not in _ASSET_CLASSES:
        raise AssetError(f"asset.assetClass {asset_class!r} is unsupported")

    output_raw = _required(asset, "output", dict, "asset")
    output_path_text = _nonempty_string(output_raw, "path", "asset.output")
    output_path = PurePosixPath(output_path_text)
    if output_path.is_absolute() or ".." in output_path.parts or output_path.suffix != ".png":
        raise AssetError("asset.output.path must be a repository-relative PNG path")
    if output_path.parts[: len(_OUTPUT_PREFIX.parts)] != _OUTPUT_PREFIX.parts:
        raise AssetError("asset.output.path must remain under the Quirky texture tree")
    output = OutputSpec(
        path=output_path,
        width=_positive_int(output_raw, "width", "asset.output", maximum=256),
        height=_positive_int(output_raw, "height", "asset.output", maximum=256),
        color_mode=_nonempty_string(output_raw, "colorMode", "asset.output"),
    )
    if output.color_mode != "rgba":
        raise AssetError("asset.output.colorMode must be rgba")

    style_profile = _nonempty_string(asset, "styleProfile", "asset")
    brief = _required(asset, "brief", dict, "asset")
    _nonempty_string(brief, "subject", "asset.brief")
    _nonempty_string(brief, "gameplayMeaning", "asset.brief")
    _string_list(brief, "visualPriority", "asset.brief", require_item=True)
    must_have = _string_list(asset, "mustHave", "asset", require_item=True)
    must_not_have = _string_list(asset, "mustNotHave", "asset", require_item=False)

    gates_raw = _required(asset, "qualityGates", dict, "asset")
    gates = QualityGates(
        maximum_palette_size=_positive_int(gates_raw, "maximumPaletteSize", "asset.qualityGates", maximum=256),
        allow_partial_alpha=_required(gates_raw, "allowPartialAlpha", bool, "asset.qualityGates"),
        require_visual_audit=_required(gates_raw, "requireVisualAudit", bool, "asset.qualityGates"),
        require_human_approval=_required(gates_raw, "requireHumanApproval", bool, "asset.qualityGates"),
    )
    if not gates.require_human_approval:
        raise AssetError("asset.qualityGates.requireHumanApproval must be true")

    _validate_source(source, output)
    spec = AssetSpec(
        asset_id=asset_id,
        asset_class=asset_class,
        output=output,
        style_profile=style_profile,
        brief=brief,
        must_have=must_have,
        must_not_have=must_not_have,
        quality_gates=gates,
    )
    return AssetPackage(package_dir=package_dir, spec=spec, source=source)


def asset_build_dir(build_root: Path, asset_id: str) -> Path:
    """Map a validated resource identifier to a path that stays inside the build root."""
    if not _valid_asset_id(asset_id):
        raise AssetError("assetId must be a lowercase quirky resource identifier")
    namespace, path = asset_id.split(":", 1)
    base = Path(build_root).resolve()
    result = (base / namespace / Path(path)).resolve()
    if not result.is_relative_to(base):
        raise AssetError("asset build path escapes the build root")
    return Path(build_root) / namespace / Path(path)
