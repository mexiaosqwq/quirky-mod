"""Validate strict JSON produced by Quirky visual agents."""

from __future__ import annotations

import json
import re
from collections.abc import Mapping
from typing import Any


class ReportError(ValueError):
    """Raised when a visual report violates its role contract."""


_EDIT_OPS = {"add", "update", "delete"}
_SEVERITIES = {"blocking", "major", "minor"}


def validate_edit_plan(value: Any, label: str) -> dict[str, Any]:
    """Validate the machine-executable edit-plan structure (semantic checks run at apply time)."""
    if not isinstance(value, Mapping):
        raise ReportError(f"{label} must be an object")
    version = value.get("version")
    if version != 1:
        raise ReportError(f"{label}.version must equal 1")
    operations = value.get("operations")
    if not isinstance(operations, list):
        raise ReportError(f"{label}.operations must be a list")
    for index, operation in enumerate(operations):
        op_label = f"{label}.operations[{index}]"
        if not isinstance(operation, Mapping):
            raise ReportError(f"{op_label} must be an object")
        op = operation.get("op")
        if op not in _EDIT_OPS:
            raise ReportError(f"{op_label}.op must be one of {sorted(_EDIT_OPS)}")
        if op == "add":
            layer = operation.get("layer")
            if not isinstance(layer, Mapping) or not isinstance(layer.get("id"), str) or not layer["id"].strip():
                raise ReportError(f"{op_label}.layer must include a non-empty id")
        elif op == "update":
            layer_id = operation.get("layerId")
            patch = operation.get("patch")
            if not isinstance(layer_id, str) or not layer_id.strip():
                raise ReportError(f"{op_label}.layerId must be a non-empty string")
            if not isinstance(patch, Mapping) or not patch:
                raise ReportError(f"{op_label}.patch must be a non-empty object")
        elif op == "delete":
            layer_id = operation.get("layerId")
            if not isinstance(layer_id, str) or not layer_id.strip():
                raise ReportError(f"{op_label}.layerId must be a non-empty string")
    return value


def _required(report: Mapping[str, Any], key: str, expected: type) -> Any:
    if key not in report:
        raise ReportError(f"missing {key}")
    value = report[key]
    if expected is bool:
        valid = type(value) is bool
    else:
        valid = isinstance(value, expected)
    if not valid:
        raise ReportError(f"{key} must be {expected.__name__}")
    return value


def _reject_approval_keys(value: Any) -> None:
    if isinstance(value, Mapping):
        for key, child in value.items():
            normalized = re.sub(r"[^a-z]", "", str(key).lower())
            if "approve" in normalized:
                raise ReportError(f"visual report must not contain approval key {key!r}")
            _reject_approval_keys(child)
    elif isinstance(value, list):
        for child in value:
            _reject_approval_keys(child)


def _validate_identity(report: Mapping[str, Any], role: str) -> None:
    if _required(report, "taskDomain", str) != "MINECRAFT_PNG_ASSET_PIPELINE":
        raise ReportError("taskDomain is invalid")
    if _required(report, "project", str) != "QUIRKY":
        raise ReportError("project is invalid")
    expected_role = "VISUAL_DESIGNER" if role == "designer" else "VISUAL_AUDITOR"
    if _required(report, "role", str) != expected_role:
        raise ReportError(f"role must be {expected_role}")
    if not _required(report, "assetId", str).startswith("quirky:"):
        raise ReportError("assetId must be a Quirky resource identifier")
    if _required(report, "imageLoaded", bool) is not True:
        raise ReportError("imageLoaded must be true")
    if _required(report, "humanReviewRequired", bool) is not True:
        raise ReportError("humanReviewRequired must be true")
    unknowns = _required(report, "unknowns", list)
    if any(not isinstance(item, str) or not item.strip() for item in unknowns):
        raise ReportError("unknowns must contain non-empty strings")


def _validate_region(region: Any, width: int, height: int, label: str) -> None:
    if not isinstance(region, Mapping):
        raise ReportError(f"{label}.region must be an object")
    values: dict[str, int] = {}
    for key in ("x", "y", "width", "height"):
        value = region.get(key)
        if type(value) is not int:
            raise ReportError(f"{label}.region.{key} must be int")
        values[key] = value
    if (
        values["x"] < 0
        or values["y"] < 0
        or values["width"] <= 0
        or values["height"] <= 0
        or values["x"] + values["width"] > width
        or values["y"] + values["height"] > height
    ):
        raise ReportError(f"{label}.region is outside the canvas")


def _validate_verdict(report: Mapping[str, Any]) -> None:
    verdict = _required(report, "verdict", str)
    if not verdict.strip():
        raise ReportError("verdict must be a non-empty string")


def _validate_auditor(report: Mapping[str, Any], width: int, height: int) -> None:
    status = _required(report, "status", str)
    if status not in {"pass_visual", "changes_required", "unknown", "blocked"}:
        raise ReportError(f"auditor status {status!r} is invalid")
    findings = _required(report, "findings", list)
    for index, finding in enumerate(findings):
        label = f"findings[{index}]"
        if not isinstance(finding, Mapping):
            raise ReportError(f"{label} must be an object")
        for key in ("visibleFact", "judgment", "recommendation"):
            value = finding.get(key)
            if not isinstance(value, str) or not value.strip():
                raise ReportError(f"{label}.{key} must be a non-empty string")
        _validate_region(finding.get("region"), width, height, label)
        severity = finding.get("severity")
        if severity not in _SEVERITIES:
            raise ReportError(f"{label}.severity must be one of {sorted(_SEVERITIES)}")
        confidence = finding.get("confidence")
        if type(confidence) not in {int, float} or not 0.0 <= confidence <= 1.0:
            raise ReportError(f"{label}.confidence must be in 0..1")


def _validate_designer(report: Mapping[str, Any]) -> None:
    observations = _required(report, "observations", list)
    proposals = _required(report, "proposals", list)
    for label, values in (("observations", observations), ("proposals", proposals)):
        if any(not isinstance(value, str) or not value.strip() for value in values):
            raise ReportError(f"{label} must contain non-empty strings")


def validate_visual_report(raw: str, role: str, width: int, height: int) -> dict[str, Any]:
    """Parse one raw JSON object and validate the designer or auditor contract."""
    if role not in {"designer", "auditor"}:
        raise ReportError("role must be designer or auditor")
    if type(width) is not int or type(height) is not int or width <= 0 or height <= 0:
        raise ReportError("canvas dimensions must be positive integers")
    stripped = raw.lstrip()
    try:
        report, end = json.JSONDecoder().raw_decode(stripped)
    except json.JSONDecodeError as exc:
        raise ReportError("report must be raw JSON without Markdown fences") from exc
    if stripped[end:].strip():
        raise ReportError("report must contain exactly one raw JSON object")
    if not isinstance(report, dict):
        raise ReportError("report must be a JSON object")
    _reject_approval_keys(report)
    _validate_identity(report, role)
    if role == "designer":
        _validate_designer(report)
    else:
        _validate_auditor(report, width, height)
    _validate_verdict(report)
    if "editPlan" in report:
        validate_edit_plan(report["editPlan"], "editPlan")
        if role == "auditor" and report.get("status") == "changes_required":
            if not report["editPlan"].get("operations"):
                raise ReportError("changes_required auditor report must provide a non-empty editPlan")
    return report
