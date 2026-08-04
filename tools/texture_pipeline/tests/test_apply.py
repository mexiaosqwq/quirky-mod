import json
import unittest

from tools.texture_pipeline.apply import ApplyError, apply_edit_plan

MINIMAL_SOURCE = {
    "canvas": {"width": 4, "height": 4, "background": "transparent"},
    "palette": {"plate": "#9AA5B1FF", "gold": "#D4AF37FF"},
    "layers": [
        {"id": "body", "operation": "rect", "color": "plate", "x": 0, "y": 0, "width": 4, "height": 4},
        {"id": "rivet", "operation": "point", "color": "gold", "x": 1, "y": 1},
    ],
}


class ApplyEditPlanTest(unittest.TestCase):
    def test_add_appends_layer(self):
        plan = {
            "version": 1,
            "operations": [
                {"op": "add", "layer": {"id": "trim", "operation": "rect", "color": "gold", "x": 0, "y": 0, "width": 2, "height": 1}}
            ],
        }
        result = apply_edit_plan(MINIMAL_SOURCE, plan)
        self.assertEqual([layer["id"] for layer in result["layers"]], ["body", "rivet", "trim"])
        self.assertIsNot(result, MINIMAL_SOURCE)

    def test_update_overwrites_fields(self):
        plan = {"version": 1, "operations": [{"op": "update", "layerId": "rivet", "patch": {"x": 3}}]}
        result = apply_edit_plan(MINIMAL_SOURCE, plan)
        self.assertEqual(result["layers"][1]["x"], 3)
        self.assertEqual(result["layers"][1]["y"], 1)

    def test_delete_removes_layer(self):
        plan = {"version": 1, "operations": [{"op": "delete", "layerId": "rivet"}]}
        result = apply_edit_plan(MINIMAL_SOURCE, plan)
        self.assertEqual([layer["id"] for layer in result["layers"]], ["body"])

    def test_update_unknown_layer_raises(self):
        plan = {"version": 1, "operations": [{"op": "update", "layerId": "nope", "patch": {"x": 1}}]}
        with self.assertRaises(ApplyError):
            apply_edit_plan(MINIMAL_SOURCE, plan)

    def test_delete_unknown_layer_raises(self):
        plan = {"version": 1, "operations": [{"op": "delete", "layerId": "nope"}]}
        with self.assertRaises(ApplyError):
            apply_edit_plan(MINIMAL_SOURCE, plan)

    def test_update_cannot_change_id(self):
        plan = {"version": 1, "operations": [{"op": "update", "layerId": "rivet", "patch": {"id": "other"}}]}
        with self.assertRaises(ApplyError):
            apply_edit_plan(MINIMAL_SOURCE, plan)

    def test_source_untouched_on_error(self):
        before = json.dumps(MINIMAL_SOURCE, sort_keys=True)
        plan = {"version": 1, "operations": [{"op": "update", "layerId": "nope", "patch": {"x": 1}}]}
        with self.assertRaises(ApplyError):
            apply_edit_plan(MINIMAL_SOURCE, plan)
        self.assertEqual(json.dumps(MINIMAL_SOURCE, sort_keys=True), before)

    def test_multiple_operations_in_order(self):
        plan = {
            "version": 1,
            "operations": [
                {"op": "add", "layer": {"id": "a", "operation": "point", "color": "gold", "x": 2, "y": 2}},
                {"op": "delete", "layerId": "rivet"},
                {"op": "add", "layer": {"id": "b", "operation": "point", "color": "plate", "x": 3, "y": 3}},
            ],
        }
        result = apply_edit_plan(MINIMAL_SOURCE, plan)
        self.assertEqual([layer["id"] for layer in result["layers"]], ["body", "a", "b"])


if __name__ == "__main__":
    unittest.main()
