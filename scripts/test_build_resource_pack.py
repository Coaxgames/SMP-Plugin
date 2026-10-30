import importlib.util
import unittest
from pathlib import Path

SCRIPT_PATH = Path(__file__).with_name("build-resource-pack.py")
SPEC = importlib.util.spec_from_file_location("build_resource_pack", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
rewrite_exported_model = MODULE.rewrite_exported_model


class RewriteExportedModelTests(unittest.TestCase):
    def test_resolves_sprite_objects_and_preserves_texture_aliases(self):
        exported = {
            "textures": {
                "1": {
                    "force_translucent": True,
                    "sprite": "block/nebula_spear_core",
                },
                "2": "block/nebula_spear_rune",
            },
            "elements": [
                {"faces": {"north": {"texture": "#1"}, "south": {"texture": "#2"}}}
            ],
        }
        textures_by_name = {
            "nebula_spear_core": {"filename": "nebula_spear_core.png"},
            "nebula_spear_rune": {"filename": "nebula_spear_rune.png"},
        }

        rewritten = rewrite_exported_model(
            Path("nebula_spear.json"), exported, textures_by_name, "nebula_spear"
        )

        self.assertEqual(
            rewritten["textures"],
            {
                "t1": "galaxy:item/nebula_spear/nebula_spear_core",
                "t2": "galaxy:item/nebula_spear/nebula_spear_rune",
            },
        )
        self.assertEqual(
            rewritten["elements"][0]["faces"]["north"]["texture"], "#t1"
        )
        self.assertEqual(
            rewritten["elements"][0]["faces"]["south"]["texture"], "#t2"
        )

    def test_rejects_texture_objects_without_a_string_sprite(self):
        exported = {"textures": {"1": {"force_translucent": True}}}

        with self.assertRaisesRegex(SystemExit, "string 'sprite'"):
            rewrite_exported_model(
                Path("nebula_spear.json"), exported, {}, "nebula_spear"
            )


if __name__ == "__main__":
    unittest.main()
