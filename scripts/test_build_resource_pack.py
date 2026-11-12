import importlib.util
import shutil
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

SCRIPT_PATH = Path(__file__).with_name("build-resource-pack.py")
SPEC = importlib.util.spec_from_file_location("build_resource_pack", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
rewrite_exported_model = MODULE.rewrite_exported_model
build_shield_blocking = MODULE.build_shield_blocking
align_shield_offhand = MODULE.align_shield_offhand
copy_pack_assets = MODULE.copy_pack_assets
split_shared_texture_ids = MODULE.split_shared_texture_ids


class CorruptPearlPackTests(unittest.TestCase):
    def test_supplied_model_and_texture_bind_only_to_corrupt_pearl(self):
        assets = SCRIPT_PATH.parent.parent / "Textures"
        with TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source = root / "source"
            source.mkdir()
            for name in ("CrackEnderPearl.bbmodel", "CrackEnderPearl.json"):
                shutil.copy2(assets / name, source / name)
            output = root / "pack"
            MODULE.main(source, output)

            galaxy = output / "assets" / "galaxy"
            item = MODULE.read_json(galaxy / "items" / "corrupt_ender_pearl.json")
            model = MODULE.read_json(galaxy / "models" / "item" / "corrupt_ender_pearl.json")
            original = MODULE.read_json(assets / "CrackEnderPearl.json")
            texture = galaxy / "textures" / "item" / "corrupt_ender_pearl" / "ender_pearl.png"
            self.assertEqual(item["model"]["model"], "galaxy:item/corrupt_ender_pearl")
            self.assertEqual(model["textures"]["particle"], "galaxy:item/corrupt_ender_pearl/ender_pearl")
            self.assertEqual(model["display"], original["display"])
            self.assertEqual(texture.read_bytes(), (assets / "ender_pearl.png").read_bytes())
            self.assertFalse((output / "assets" / "minecraft" / "items" / "ender_pearl.json").exists())


class GalaxyStringPackTests(unittest.TestCase):
    def test_supplied_model_texture_and_animation_are_bound(self):
        assets = SCRIPT_PATH.parent.parent / "Textures"
        with TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source = root / "source"
            source.mkdir()
            for name in (
                "CrackEnderPearl.bbmodel", "CrackEnderPearl.json",
                "galaxy_string.json", "galaxy_string.png", "galaxy_string.png.mcmeta",
            ):
                shutil.copy2(assets / name, source / name)
            output = root / "pack"
            MODULE.main(source, output)

            galaxy = output / "assets" / "galaxy"
            item = MODULE.read_json(galaxy / "items" / "galaxy_string.json")
            model = MODULE.read_json(galaxy / "models" / "item" / "galaxy_string.json")
            texture = galaxy / "textures" / "item" / "galaxy_string" / "galaxy_string.png"
            self.assertEqual(item["model"]["model"], "galaxy:item/galaxy_string")
            self.assertEqual(model["textures"]["layer0"], "galaxy:item/galaxy_string/galaxy_string")
            self.assertEqual(texture.read_bytes(), (assets / "galaxy_string.png").read_bytes())
            self.assertEqual(
                texture.with_suffix(".png.mcmeta").read_bytes(),
                (assets / "galaxy_string.png.mcmeta").read_bytes(),
            )


class SplitSharedTextureIdsTests(unittest.TestCase):
    def test_faces_keep_their_own_texture_when_ids_are_shared(self):
        source = {
            "textures": [
                {"id": "spike", "name": "gold.png"},
                {"id": "spike", "name": "midnight.png"},
                {"id": "4", "name": "swirl.png"},
            ],
            "elements": [
                {"name": "a", "from": [0, 0, 0], "to": [1, 1, 1],
                 "faces": {"north": {"texture": 0}, "south": {"texture": 1}, "east": {"texture": 2}}},
            ],
        }
        exported = {
            "textures": {"spike": "midnight", "4": "swirl"},
            "elements": [
                {"name": "a", "from": [0, 0, 0], "to": [1, 1, 1],
                 "faces": {"north": {"texture": "#spike"}, "south": {"texture": "#spike"},
                           "east": {"texture": "#4"}}},
            ],
        }
        split_shared_texture_ids(exported, source)
        faces = exported["elements"][0]["faces"]
        self.assertEqual(faces["north"]["texture"], "#tex_gold")
        self.assertEqual(faces["south"]["texture"], "#tex_midnight")
        self.assertEqual(faces["east"]["texture"], "#4")
        self.assertEqual(exported["textures"]["tex_gold"], "gold.png")


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


class CopyPackAssetsTests(unittest.TestCase):
    def test_copies_pack_assets_to_matching_relative_paths(self):
        with TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source = root / "Textures"
            asset = source / "pack_assets" / "assets" / "minecraft" / "equipment" / "lunar_leggings.json"
            asset.parent.mkdir(parents=True)
            asset.write_text('{"layers": {}}', encoding="utf-8")
            output = root / "output"
            copy_pack_assets(source, output)
            self.assertEqual(
                (output / "assets" / "minecraft" / "equipment" / "lunar_leggings.json").read_text(
                    encoding="utf-8"
                ),
                '{"layers": {}}',
            )


class ApplyLooseAnimationTests(unittest.TestCase):
    def test_hand_written_animation_overrides_only_the_given_keys(self):
        with TemporaryDirectory() as temporary_directory:
            texture = Path(temporary_directory) / "rift.png"
            texture.with_suffix(".png.mcmeta").write_text(
                '{"animation": {"width": 16, "height": 16, "frametime": 1, "frames": [0, 1]}}',
                encoding="utf-8",
            )
            MODULE.apply_loose_animation(
                texture, {"animation": {"frametime": 2, "interpolate": True}}
            )
            merged = MODULE.read_json(texture.with_suffix(".png.mcmeta"))["animation"]
            self.assertEqual(
                merged,
                {"width": 16, "height": 16, "frametime": 2, "frames": [0, 1], "interpolate": True},
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
