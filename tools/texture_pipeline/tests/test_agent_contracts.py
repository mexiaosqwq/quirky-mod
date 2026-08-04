import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
AGENT_DIR = REPOSITORY_ROOT / ".pi/agents"


def read_agent(name):
    path = AGENT_DIR / f"{name}.md"
    text = path.read_text(encoding="utf-8")
    self_closing, frontmatter, body = text.split("---", 2)
    if self_closing.strip():
        raise AssertionError("agent file must start with YAML frontmatter")
    fields = {}
    for line in frontmatter.strip().splitlines():
        key, value = line.split(":", 1)
        fields[key.strip()] = value.strip()
    return fields, body


class VisualAgentContractTest(unittest.TestCase):
    def assert_common_contract(self, name, expected_role):
        fields, body = read_agent(name)
        self.assertEqual(fields["model"], "gorouter/claude-opus-5-thinking")
        self.assertEqual(fields["thinking"], "high")
        self.assertEqual(fields["tools"], "read")
        self.assertEqual(fields["prompt_mode"], "replace")
        self.assertEqual(fields["inherit_context"], "false")
        self.assertEqual(fields["isolated"], "true")
        self.assertEqual(fields["output_transcript"], "false")
        self.assertIn("TASK_DOMAIN: MINECRAFT_PNG_ASSET_PIPELINE", body)
        self.assertIn(f"ROLE: {expected_role}", body)
        self.assertIn("FINAL_AUTHORITY: HUMAN", body)
        self.assertIn("read-only", body)
        self.assertIn("raw JSON", body)
        self.assertIn('"imageLoaded"', body)
        self.assertIn('"humanReviewRequired": true', body)
        self.assertNotIn("write tool", body.lower())
        return body

    def test_designer_is_read_only_and_returns_design_slots(self):
        body = self.assert_common_contract("texture-visual-designer", "VISUAL_DESIGNER")
        self.assertIn('"observations"', body)
        self.assertIn('"proposals"', body)
        self.assertIn("must not approve", body.lower())

    def test_auditor_is_independent_and_returns_evidence_slots(self):
        body = self.assert_common_contract("texture-visual-auditor", "VISUAL_AUDITOR")
        self.assertIn("fresh independent session", body.lower())
        self.assertIn("designer transcript", body.lower())
        for slot in ("visibleFact", "judgment", "recommendation", "region", "confidence"):
            self.assertIn(f'"{slot}"', body)
        self.assertIn("pass_visual", body)
        self.assertIn("not final approval", body.lower())


if __name__ == "__main__":
    unittest.main()
