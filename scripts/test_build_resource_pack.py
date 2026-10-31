import importlib.util
import unittest
from pathlib import Path

SCRIPT_PATH = Path(__file__).with_name("build-resource-pack.py")
SPEC = importlib.util.spec_from_file_location("build_resource_pack", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
rewrite_exported_model = MODULE.rewrite_exported_model
build_shield_blocking = MODULE.build_shield_blocking
align_shield_offhand = MODULE.align_shield_offhand


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

    def test_rewrites_parented_generated_item_model(self):
        exported = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "ability_spark"},
        }
        textures_by_name = {}
        loose_textures = {"ability_spark": "galaxy:source/ability_spark"}

        rewritten = rewrite_exported_model(
            Path("ability_spark.json"),
            exported,
            textures_by_name,
            "ability_spark",
            loose_textures,
        )

        self.assertEqual(rewritten["parent"], "minecraft:item/generated")
        self.assertEqual(
            rewritten["textures"], {"layer0": "galaxy:source/ability_spark"}
        )


class BuildShieldBlockingTests(unittest.TestCase):
    def test_idle_first_person_offhand_matches_main_hand_angle(self):
        model = {
            "display": {
                "firstperson_righthand": {"rotation": [0, -90, 25]},
                "firstperson_lefthand": {"rotation": [54.5, -79.75, 65]},
                "thirdperson_righthand": {"scale": [1.196, 1.1385, 1.127]},
                "thirdperson_lefthand": {"scale": [0.92, 0.92, 0.92]},
            }
        }

        aligned = align_shield_offhand(model)

        self.assertEqual(
            aligned["display"]["firstperson_lefthand"]["rotation"], [0, -90, 25]
        )
        self.assertEqual(
            aligned["display"]["thirdperson_lefthand"]["scale"],
            [1.196, 1.1385, 1.127],
        )
        self.assertEqual(model["display"]["firstperson_lefthand"]["rotation"], [54.5, -79.75, 65])

    def test_first_person_blocking_faces_forward_without_tilt(self):
        model = {
            "display": {
                "firstperson_righthand": {
                    "rotation": [0, -90, 25],
                    "translation": [2.13, -0.05, 1.13],
                    "scale": [0.8, 0.8, 0.8],
                },
                "firstperson_lefthand": {
                    "rotation": [54.5, -79.75, 65],
                    "translation": [0.63, 1.2, 3.38],
                    "scale": [0.8, 0.8, 0.8],
                },
            }
        }

        blocking = build_shield_blocking(align_shield_offhand(model))

        self.assertEqual(
            blocking["display"]["firstperson_righthand"]["rotation"], [0, 0, 0]
        )
        self.assertEqual(
            blocking["display"]["firstperson_lefthand"]["rotation"], [0, 0, 0]
        )
        self.assertEqual(model["display"]["firstperson_lefthand"]["rotation"], [54.5, -79.75, 65])


if __name__ == "__main__":
    unittest.main()
