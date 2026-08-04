import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
SKILL = REPOSITORY_ROOT / ".pi/skills/quirky-texture-workflow/SKILL.md"


class TextureWorkflowSkillContractTest(unittest.TestCase):
    def test_skill_teaches_exact_project_workflow(self):
        text = SKILL.read_text(encoding="utf-8")
        first, frontmatter, body = text.split("---", 2)
        self.assertEqual(first.strip(), "")
        fields = {}
        for line in frontmatter.strip().splitlines():
            key, value = line.split(":", 1)
            fields[key.strip()] = value.strip()
        self.assertEqual(fields["name"], "quirky-texture-workflow")
        self.assertTrue(fields["description"].startswith("Use when"))
        for phrase in (
            "Route A",
            "Route B",
            "tools/texture_pipeline/assets/",
            "build/texture-pipeline/",
            "src/main/resources/assets/quirky/",
            "texture-visual-designer",
            "texture-visual-auditor",
            "python -m tools.texture_pipeline validate",
            "python -m tools.texture_pipeline render",
            "python -m tools.texture_pipeline check-report",
            "candidates/<candidate-id>.png",
            "reports/{pixel-facts,validation,visual-design,visual-audit}.json",
            "output.path",
            "do not send them to a visual role",
            "user is the only final approver",
            "retry it once",
            "Do not run Gradle",
        ):
            self.assertIn(phrase, body)
        self.assertLessEqual(len(body.split()), 750)


if __name__ == "__main__":
    unittest.main()
