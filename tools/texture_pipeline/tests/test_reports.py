import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from tools.texture_pipeline.reports import ReportError, validate_visual_report


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]


def auditor_report():
    return {
        "taskDomain": "MINECRAFT_PNG_ASSET_PIPELINE",
        "project": "QUIRKY",
        "role": "VISUAL_AUDITOR",
        "assetId": "quirky:item/example",
        "imageLoaded": True,
        "verdict": "整体读作胸甲，金色略多",
        "status": "changes_required",
        "findings": [
            {
                "visibleFact": "The top two colors merge at native scale",
                "judgment": "The opening is difficult to read",
                "recommendation": "Increase value separation by one palette step",
                "severity": "major",
                "region": {"x": 4, "y": 1, "width": 6, "height": 3},
                "confidence": 0.9,
            }
        ],
        "editPlan": {
            "version": 1,
            "operations": [{"op": "update", "layerId": "body", "patch": {"x": 1}}],
        },
        "unknowns": [],
        "humanReviewRequired": True,
    }


def designer_report():
    return {
        "taskDomain": "MINECRAFT_PNG_ASSET_PIPELINE",
        "project": "QUIRKY",
        "role": "VISUAL_DESIGNER",
        "assetId": "quirky:item/example",
        "imageLoaded": True,
        "verdict": "整体读作胸甲，肩部可再收窄",
        "observations": ["The silhouette reads as a pouch"],
        "proposals": ["Separate the rim and contents with one value step"],
        "unknowns": [],
        "humanReviewRequired": True,
    }


class VisualReportTest(unittest.TestCase):
    def test_accepts_canonical_designer_and_auditor_reports(self):
        designer = validate_visual_report(json.dumps(designer_report()), "designer", 16, 16)
        auditor = validate_visual_report(json.dumps(auditor_report()), "auditor", 16, 16)

        self.assertEqual(designer["role"], "VISUAL_DESIGNER")
        self.assertEqual(auditor["status"], "changes_required")

    def test_rejects_markdown_wrapped_json(self):
        raw = "```json\n" + json.dumps(auditor_report()) + "\n```"
        with self.assertRaisesRegex(ReportError, "raw JSON"):
            validate_visual_report(raw, "auditor", 16, 16)

    def test_rejects_image_load_failure(self):
        report = auditor_report()
        report["imageLoaded"] = False
        with self.assertRaisesRegex(ReportError, "imageLoaded"):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)

    def test_rejects_role_mismatch_and_unknown_status(self):
        with self.assertRaisesRegex(ReportError, "role"):
            validate_visual_report(json.dumps(auditor_report()), "designer", 16, 16)
        report = auditor_report()
        report["status"] = "approved"
        with self.assertRaisesRegex(ReportError, "status"):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)

    def test_rejects_approval_claim_anywhere(self):
        report = auditor_report()
        report["metadata"] = {"finalApproved": True}
        with self.assertRaisesRegex(ReportError, "approval"):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)

    def test_rejects_out_of_range_region(self):
        report = auditor_report()
        report["findings"][0]["region"] = {"x": 15, "y": 1, "width": 2, "height": 3}
        with self.assertRaisesRegex(ReportError, "region"):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)

    def test_rejects_invalid_confidence_and_missing_finding_slot(self):
        report = auditor_report()
        report["findings"][0]["confidence"] = 1.1
        with self.assertRaisesRegex(ReportError, "confidence"):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)
        report = auditor_report()
        del report["findings"][0]["visibleFact"]
        with self.assertRaisesRegex(ReportError, "visibleFact"):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)

    def test_cli_accepts_valid_report_and_rejects_invalid_report(self):
        with tempfile.TemporaryDirectory() as directory:
            valid = Path(directory) / "valid.json"
            invalid = Path(directory) / "invalid.json"
            valid.write_text(json.dumps(auditor_report()), encoding="utf-8")
            invalid.write_text("```json\n{}\n```", encoding="utf-8")
            command = [sys.executable, "-m", "tools.texture_pipeline", "check-report"]

            accepted = subprocess.run(
                command + [str(valid), "--role", "auditor", "--width", "16", "--height", "16"],
                cwd=REPOSITORY_ROOT,
                capture_output=True,
                text=True,
            )
            rejected = subprocess.run(
                command + [str(invalid), "--role", "auditor", "--width", "16", "--height", "16"],
                cwd=REPOSITORY_ROOT,
                capture_output=True,
                text=True,
            )

            self.assertEqual(accepted.returncode, 0, accepted.stderr)
            self.assertIn('"role": "VISUAL_AUDITOR"', accepted.stdout)
            self.assertEqual(rejected.returncode, 2)
            self.assertTrue(rejected.stderr.startswith("ERROR:"), rejected.stderr)


class EditPlanContractTest(unittest.TestCase):
    def test_verdict_required(self):
        report = auditor_report()
        del report["verdict"]
        with self.assertRaises(ReportError):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)

    def test_verdict_must_be_nonempty_string(self):
        report = auditor_report()
        report["verdict"] = ""
        with self.assertRaises(ReportError):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)

    def test_auditor_findings_require_severity(self):
        report = auditor_report()
        del report["findings"][0]["severity"]
        with self.assertRaises(ReportError):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)

    def test_severity_must_be_enum(self):
        report = auditor_report()
        report["findings"][0]["severity"] = "urgent"
        with self.assertRaises(ReportError):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)

    def test_changes_required_requires_nonempty_editplan(self):
        report = auditor_report()
        report["editPlan"] = {"version": 1, "operations": []}
        with self.assertRaises(ReportError):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)

    def test_changes_required_missing_editplan_rejected(self):
        report = auditor_report()
        del report["editPlan"]
        with self.assertRaises(ReportError):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)

    def test_changes_required_with_editplan_passes(self):
        report = auditor_report()
        result = validate_visual_report(json.dumps(report), "auditor", 16, 16)
        self.assertEqual(result["editPlan"]["operations"][0]["op"], "update")

    def test_pass_visual_allows_empty_editplan(self):
        report = auditor_report()
        report["status"] = "pass_visual"
        report["editPlan"] = {"version": 1, "operations": []}
        result = validate_visual_report(json.dumps(report), "auditor", 16, 16)
        self.assertEqual(result["status"], "pass_visual")

    def test_editplan_unknown_op_rejected(self):
        report = auditor_report()
        report["editPlan"] = {"version": 1, "operations": [{"op": "explode", "layerId": "a"}]}
        with self.assertRaises(ReportError):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)

    def test_editplan_add_requires_layer(self):
        report = auditor_report()
        report["editPlan"] = {"version": 1, "operations": [{"op": "add"}]}
        with self.assertRaises(ReportError):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)

    def test_editplan_update_requires_layerid_and_patch(self):
        report = auditor_report()
        report["editPlan"] = {"version": 1, "operations": [{"op": "update", "layerId": "a"}]}
        with self.assertRaises(ReportError):
            validate_visual_report(json.dumps(report), "auditor", 16, 16)

    def test_designer_also_supports_editplan(self):
        report = designer_report()
        report["editPlan"] = {
            "version": 1,
            "operations": [
                {"op": "add", "layer": {"id": "n", "operation": "point", "color": "transparent", "x": 1, "y": 1}}
            ],
        }
        result = validate_visual_report(json.dumps(report), "designer", 16, 16)
        self.assertEqual(len(result["editPlan"]["operations"]), 1)

    def test_validate_edit_plan_rejects_non_object(self):
        from tools.texture_pipeline.reports import validate_edit_plan

        with self.assertRaises(ReportError):
            validate_edit_plan(["not", "an", "object"], "editPlan")

    def test_designer_requires_verdict_too(self):
        report = designer_report()
        del report["verdict"]
        with self.assertRaises(ReportError):
            validate_visual_report(json.dumps(report), "designer", 16, 16)


if __name__ == "__main__":
    unittest.main()
