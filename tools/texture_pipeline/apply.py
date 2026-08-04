"""Apply a validated edit plan to a structured texture source."""

from __future__ import annotations

import copy
from collections.abc import Mapping
from typing import Any


class ApplyError(ValueError):
    """Raised when an edit plan cannot be applied to a source."""


def apply_edit_plan(source: Mapping[str, Any], plan: Mapping[str, Any]) -> dict[str, Any]:
    """Return a deep copy of `source` with the plan's operations applied in order."""
    result = copy.deepcopy(source)
    layers = result["layers"]
    by_id = {layer["id"]: layer for layer in layers}
    for index, operation in enumerate(plan["operations"]):
        op = operation["op"]
        if op == "add":
            layer = copy.deepcopy(operation["layer"])
            if layer["id"] in by_id:
                raise ApplyError(f"editPlan.operations[{index}] add: duplicate layer id {layer['id']!r}")
            by_id[layer["id"]] = layer
            layers.append(layer)
        elif op == "update":
            layer_id = operation["layerId"]
            if layer_id not in by_id:
                raise ApplyError(f"editPlan.operations[{index}] update: unknown layer {layer_id!r}")
            layer = by_id[layer_id]
            for key, value in operation["patch"].items():
                if key == "id":
                    raise ApplyError(f"editPlan.operations[{index}] update: cannot change layer id")
                layer[key] = copy.deepcopy(value)
        elif op == "delete":
            layer_id = operation["layerId"]
            if layer_id not in by_id:
                raise ApplyError(f"editPlan.operations[{index}] delete: unknown layer {layer_id!r}")
            by_id.pop(layer_id)
            layers[:] = [layer for layer in layers if layer["id"] != layer_id]
    return result
