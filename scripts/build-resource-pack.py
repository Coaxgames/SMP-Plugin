import base64
import json
import re
import shutil
import struct
import sys
import zlib
from pathlib import Path


PACK_FORMAT = [97, 1]


def fail(message):
    raise SystemExit(message)


def slug(value):
    value = Path(value).with_suffix("").as_posix()
    result = re.sub(r"[^a-zA-Z0-9._/-]+", "_", value).strip("_").lower()
    result = re.sub(r"/+", "/", result)
    if not result:
        fail(f"Cannot create a resource name from {value!r}.")
    return result


def read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"Could not read JSON model {path}: {error}")


def extract_textures(model_path, model, texture_dir):
    textures_by_index = {}
    textures_by_name = {}
    texture_list = model.get("textures", [])
    for index, texture in enumerate(texture_list):
        name = texture.get("name")
        source = texture.get("source", "")
        if not name or not source.startswith("data:image/png;base64,"):
            fail(f"{model_path.name}: texture {index} is not embedded as a PNG.")
        try:
            image = base64.b64decode(source.split(",", 1)[1], validate=True)
        except ValueError as error:
            fail(f"{model_path.name}: texture {name!r} has invalid base64 data: {error}")
        if image[:8] != b"\x89PNG\r\n\x1a\n" or len(image) < 24:
            fail(f"{model_path.name}: texture {name!r} is not a valid PNG.")
        width, height = struct.unpack(">II", image[16:24])
        if width != texture.get("width") or height != texture.get("height"):
            fail(f"{model_path.name}: texture dimensions do not match {name!r}'s PNG data.")

        filename = slug(name) + ".png"
        target = texture_dir / filename
        if target.exists() and target.read_bytes() != image:
            fail(f"{model_path.name}: texture name collision for {filename}.")
        target.write_bytes(image)
        texture_info = {
            "filename": filename,
            "width": width,
            "height": height,
            "name": name,
        }
        textures_by_index[index] = texture_info
        textures_by_name[slug(name)] = texture_info
        if height > width * 8:
            frame_height = texture.get("frame_height")
            if frame_height is None:
                frame_height = width if height % width == 0 else None
            if frame_height is None:
                candidates = [
                    candidate
                    for candidate in range(max(1, width * 3 // 4), width * 5 // 4 + 1)
                    if height % candidate == 0
                ]
                if candidates:
                    frame_height = min(candidates, key=lambda candidate: abs(candidate - width))
            if frame_height is None or frame_height <= 0 or height % frame_height:
                fail(
                    f"{model_path.name}: cannot determine flipbook frame height for "
                    f"{name!r} ({width}x{height}); specify a valid frame_height."
                )
            frame_count = height // frame_height
            frame_time = texture.get("frame_time", 1)
            if not isinstance(frame_time, int) or frame_time < 1:
                fail(f"{model_path.name}: invalid flipbook frame_time for {name!r}.")
            metadata = {
                "animation": {
                    "width": width,
                    "height": frame_height,
                    "frametime": frame_time,
                    "frames": list(range(frame_count)),
                }
            }
            target.with_suffix(".png.mcmeta").write_text(
                json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
            )
    return textures_by_index, textures_by_name


def resolve_texture(name, textures_by_name, model_path, loose_textures=None):
    key = slug(name)
    texture = textures_by_name.get(key)
    if texture is not None:
        return texture["filename"][:-4]
    basename = slug(Path(name).name)
    texture = textures_by_name.get(basename)
    if texture is not None:
        return texture["filename"][:-4]
    if loose_textures and key in loose_textures:
        return loose_textures[key]
    if loose_textures and basename in loose_textures:
        return loose_textures[basename]
    if ":" in name:
        return name
    fail(f"{model_path.name}: exported model references missing texture {name!r}.")


def resource_location(texture, model_id):
    if ":" in texture:
        return texture
    return f"galaxy:item/{model_id}/{texture}"


def rewrite_exported_model(model_path, exported, textures_by_name, model_id, loose_textures=None):
    old_textures = exported.get("textures", {})
    new_textures = {}
    aliases = {
        f"#{key}": f"#{'t' + key if key.isdigit() else key}"
        for key in old_textures
    }
    for key, value in old_textures.items():
        alias = f"t{key}" if key.isdigit() else key
        if isinstance(value, dict):
            value = value.get("sprite")
        if not isinstance(value, str):
            fail(
                f"{model_path.name}: texture {key!r} must be a string or an "
                "object with a string 'sprite'."
            )
        if value.startswith("#"):
            new_textures[alias] = aliases.get(value, value)
        else:
            resolved = resolve_texture(value, textures_by_name, model_path, loose_textures)
            new_textures[alias] = resource_location(resolved, model_id)

    rewritten = dict(exported)
    rewritten.pop("groups", None)
    rewritten["textures"] = new_textures
    for element in rewritten.get("elements", []):
        for face in element.get("faces", {}).values():
            texture = face.get("texture")
            if texture in aliases:
                face["texture"] = aliases[texture]
            elif isinstance(texture, str) and not texture.startswith("#"):
                resolved = resolve_texture(texture, textures_by_name, model_path, loose_textures)
                face["texture"] = resource_location(resolved, model_id)
            # The client rejects the whole model if a UV lies outside the 0-16 texture space.
            uv = face.get("uv")
            if isinstance(uv, list) and len(uv) == 4 and (min(uv) < 0 or max(uv) > 16):
                shift_u = (min(uv[0], uv[2]) // 16) * 16
                shift_v = (min(uv[1], uv[3]) // 16) * 16
                face["uv"] = [
                    min(16, max(0, round(uv[0] - shift_u, 4))),
                    min(16, max(0, round(uv[1] - shift_v, 4))),
                    min(16, max(0, round(uv[2] - shift_u, 4))),
                    min(16, max(0, round(uv[3] - shift_v, 4))),
                ]
    # Blockbench exports untextured faces as "#missing", which renders purple/black.
    kept_elements = []
    for element in rewritten.get("elements", []):
        faces = element.get("faces", {})
        element["faces"] = {
            name: face for name, face in faces.items()
            if not (isinstance(face.get("texture"), str)
                    and face["texture"].startswith("#")
                    and face["texture"][1:] not in new_textures)
        }
        if element["faces"]:
            kept_elements.append(element)
    if "elements" in rewritten:
        rewritten["elements"] = kept_elements
    return rewritten


def convert_bbmodel_elements(source_model, exported):
    """Rebuild a Java item model from the .bbmodel when its exported JSON is stale."""
    textures = source_model.get("textures", [])
    rebuilt = dict(exported)
    rebuilt.pop("groups", None)
    rebuilt["textures"] = {
        slug(texture["name"]): Path(texture["name"]).with_suffix("").as_posix()
        for texture in textures
    }
    particle = (exported.get("textures") or {}).get("particle")
    if isinstance(particle, str):
        rebuilt["textures"]["particle"] = particle
    elif textures:
        rebuilt["textures"]["particle"] = next(iter(rebuilt["textures"].values()))
    elements = []
    for source in source_model.get("elements", []):
        if source.get("type", "cube") != "cube":
            continue
        element = {"name": source.get("name", ""), "from": source["from"], "to": source["to"]}
        angles = source.get("rotation") or [0, 0, 0]
        origin = source.get("origin", [8, 8, 8])
        active = [axis for axis, value in zip("xyz", angles) if value]
        if len(active) == 1:
            axis = active[0]
            element["rotation"] = {
                "angle": angles["xyz".index(axis)], "axis": axis, "origin": origin,
            }
        elif active:
            element["rotation"] = {
                "x": angles[0], "y": angles[1], "z": angles[2], "origin": origin,
            }
        faces = {}
        for name, face in source.get("faces", {}).items():
            index = face.get("texture")
            if not isinstance(index, int) or not 0 <= index < len(textures):
                continue
            output = {"uv": face.get("uv", [0, 0, 16, 16]), "texture": "#" + slug(textures[index]["name"])}
            if face.get("rotation"):
                output["rotation"] = face["rotation"]
            faces[name] = output
        if faces:
            element["faces"] = faces
            elements.append(element)
    rebuilt["elements"] = elements
    if source_model.get("display"):
        rebuilt["display"] = source_model["display"]
    return rebuilt


def repair_export_textures(source_model, exported):
    """Rebuild face textures from the .bbmodel when Blockbench's Java export lost them.

    The export collapses textures sharing an id and can omit the textures block entirely.
    """
    source_cubes = [e for e in source_model.get("elements", []) if e.get("type", "cube") == "cube"]
    if source_cubes and len(source_cubes) != len(exported.get("elements", [])):
        return convert_bbmodel_elements(source_model, exported)
    source_textures = source_model.get("textures", [])
    ids = [texture.get("id") for texture in source_textures]
    old_textures = exported.get("textures") or {}
    referenced = {
        face.get("texture", "")[1:]
        for element in exported.get("elements", [])
        for face in element.get("faces", {}).values()
        if isinstance(face.get("texture"), str) and face["texture"].startswith("#")
    }
    if len(ids) == len(set(ids)) and referenced <= set(old_textures):
        return exported

    def element_key(element):
        return (element.get("name"), tuple(element.get("from", ())), tuple(element.get("to", ())))

    pending = {}
    for element in source_model.get("elements", []):
        pending.setdefault(element_key(element), []).append(element)

    def texture_key(index):
        return slug(source_textures[index]["name"])

    used = set()
    repaired = dict(exported)
    repaired_elements = []
    for element in exported.get("elements", []):
        element = dict(element)
        candidates = pending.get(element_key(element))
        source_element = candidates.pop(0) if candidates else None
        faces = {}
        for name, face in element.get("faces", {}).items():
            face = dict(face)
            index = (source_element or {}).get("faces", {}).get(name, {}).get("texture")
            if isinstance(index, int) and 0 <= index < len(source_textures):
                face["texture"] = "#" + texture_key(index)
                used.add(index)
            faces[name] = face
        element["faces"] = faces
        repaired_elements.append(element)
    repaired["elements"] = repaired_elements

    textures = {
        texture_key(index): Path(source_textures[index]["name"]).with_suffix("").as_posix()
        for index in sorted(used)
    }
    particle = old_textures.get("particle")
    if isinstance(particle, str):
        textures["particle"] = particle
    elif textures:
        textures["particle"] = next(iter(textures.values()))
    repaired["textures"] = textures
    return repaired


def compensate_drawn_arm(model):
    """Raise the third-person x rotation by 90 degrees for drawn-bow models.

    Minecraft lifts the arm ~90 degrees while a bow is drawn, which carries the item with it.
    The x rotation is the outermost one, so the offset adds directly, and the translation
    (applied in the arm's frame) is turned the same way. First person is left untouched.
    """
    display = model.get("display") or {}
    for key in ("thirdperson_righthand", "thirdperson_lefthand"):
        transform = display.get(key)
        if not transform:
            continue
        rotation = transform.get("rotation", [0, 0, 0])
        transform["rotation"] = [round(rotation[0] + 90, 4), rotation[1], rotation[2]]
        translation = transform.get("translation")
        if translation:
            transform["translation"] = [translation[0], -translation[2], translation[1]]


def build_shield_blocking(model):
    """Blocking variant: turn the shield 90 degrees about the vertical so its face points forward
    across the chest, where the game's raised blocking arm carries it."""
    blocking = dict(model)
    display = {key: dict(value) for key, value in model.get("display", {}).items()}
    for key in ("thirdperson_righthand", "thirdperson_lefthand"):
        transform = display.get(key)
        if transform:
            rotation = transform.get("rotation", [0, 0, 0])
            # +90 y was the closest pose but pointed at the ground because the blocking arm is raised
            # about 54 degrees; +36 x tips the face up to horizontal.
            transform["rotation"] = [round(rotation[0] + 36, 4), round(rotation[1] + 90, 4), rotation[2]]
            # Slide the shield in toward the body line and out ahead of the hand.
            translation = transform.get("translation", [0, 0, 0])
            transform["translation"] = [round(translation[0] - 4, 4), translation[1], round(translation[2] - 3, 4)]
    for key, horizontal_offset in (
        ("firstperson_righthand", -2),
        ("firstperson_lefthand", 2),
    ):
        transform = display.get(key)
        if transform:
            rotation = transform.get("rotation", [0, 0, 0])
            transform["rotation"] = [
                0,
                round(rotation[1] + 90, 4),
                0,
            ]
            translation = transform.get("translation", [0, 0, 0])
            transform["translation"] = [
                round(translation[0] + horizontal_offset, 4),
                round(translation[1] + 1.5, 4),
                round(translation[2] - 6, 4),
            ]
    blocking["display"] = display
    return blocking


def align_shield_offhand(model):
    display = {key: dict(value) for key, value in model.get("display", {}).items()}
    right = display.get("firstperson_righthand")
    left = display.get("firstperson_lefthand")
    if right and left:
        left["rotation"] = list(right.get("rotation", [0, 0, 0]))
    right = display.get("thirdperson_righthand")
    left = display.get("thirdperson_lefthand")
    if right and left:
        left["scale"] = list(right.get("scale", [1, 1, 1]))
    aligned = dict(model)
    aligned["display"] = display
    return aligned


def write_boots_equipment(output_dir):
    """Worn armor layer so the Singularity Boots render on the feet like normal boots."""
    equipment_dir = output_dir / "assets" / "galaxy" / "equipment"
    texture_dir = output_dir / "assets" / "galaxy" / "textures" / "entity" / "equipment" / "humanoid"
    equipment_dir.mkdir(parents=True, exist_ok=True)
    texture_dir.mkdir(parents=True, exist_ok=True)
    (equipment_dir / "singularity_boots.json").write_text(
        json.dumps({"layers": {"humanoid": [{"texture": "galaxy:singularity_boots"}]}}, indent=2) + "\n",
        encoding="utf-8",
    )
    width, height = 64, 32
    pixels = [[(0, 0, 0, 0)] * width for _ in range(height)]
    # Palette sampled from the 3D boots: near-black space body, orange accretion glow, cream highlights.
    space = [(8, 9, 23, 255), (7, 8, 20, 255), (10, 11, 28, 255), (14, 13, 34, 255)]
    gunmetal = (44, 40, 52, 255)
    ember = [(65, 20, 9, 255), (106, 37, 12, 255), (172, 66, 18, 255), (222, 98, 28, 255)]
    cream = (255, 248, 222, 255)
    void = (2, 2, 6, 255)
    boot_top = 26  # boots cover the bottom six rows of each leg column
    pixels_x = (0, 4, 8, 12)  # side, front, side, back faces of the leg box

    def paint(x0, y0, x1, y1, color_for):
        for y in range(y0, y1):
            for x in range(x0, x1):
                pixels[y][x] = color_for(x, y)

    def body(x, y):
        if y == boot_top:
            return gunmetal
        if y == height - 1:
            return ember[0]
        return space[(x * 7 + y * 13) % len(space)]

    for x0 in pixels_x:
        paint(x0, boot_top, x0 + 4, height, body)
    paint(4, 16, 8, 20, lambda x, y: space[0])
    paint(8, 16, 12, 20, lambda x, y: ember[0])

    # Orange cuff trim and glowing sole welt.
    for x0 in pixels_x:
        for x in range(x0, x0 + 4):
            pixels[boot_top + 1][x] = ember[1 + (x % 2)]
            pixels[height - 2][x] = ember[2]
    # Front toe glow and cream instep stripe.
    for x in (5, 6):
        pixels[boot_top + 3][x] = cream
        pixels[boot_top + 4][x] = ember[3]
    # Black hole with an accretion ring on both outer sides, as on the model's shin.
    for x0 in (0, 8):
        pixels[boot_top + 2][x0 + 1] = ember[3]
        pixels[boot_top + 2][x0 + 2] = ember[2]
        pixels[boot_top + 3][x0 + 1] = ember[2]
        pixels[boot_top + 3][x0 + 2] = void
        pixels[boot_top + 4][x0 + 1] = void
        pixels[boot_top + 4][x0 + 2] = ember[3]
    # Rear heel glow.
    pixels[boot_top + 4][13] = ember[3]
    pixels[boot_top + 4][14] = cream
    raw = b"".join(
        b"\x00" + b"".join(bytes(pixel) for pixel in row) for row in pixels
    )

    def chunk(kind, data):
        body_bytes = kind + data
        return struct.pack(">I", len(data)) + body_bytes + struct.pack(">I", zlib.crc32(body_bytes) & 0xFFFFFFFF)

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw))
        + chunk(b"IEND", b"")
    )
    (texture_dir / "singularity_boots.png").write_bytes(png)


FIRST_PERSON_OVERRIDES = {
    "star_mace": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.9, 0.9, 0.9]},
    "collapsing_star_mace": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.9, 0.9, 0.9]},
    "cosmic_crown": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.9, 0.9, 0.9]},
    "singularity_boots": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.8, 0.8, 0.8]},
}


def main(source_dir, output_dir):
    source_dir = Path(source_dir)
    output_dir = Path(output_dir)
    if not source_dir.is_dir():
        fail(f"Texture source directory does not exist: {source_dir}")
    source_root = source_dir.resolve()
    output_root = output_dir.resolve()
    if output_root == source_root or source_root in output_root.parents:
        fail("Generated resource-pack output must not be inside the Textures source directory.")
    if output_dir.exists():
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True)

    (output_dir / "pack.mcmeta").write_text(
        json.dumps(
            {
                "pack": {
                    "description": "Galaxy SMP custom item models (Minecraft 26.3)",
                    "min_format": PACK_FORMAT,
                    "max_format": PACK_FORMAT,
                }
            },
            indent=2,
        ) + "\n",
        encoding="utf-8",
    )

    atlas_dir = output_dir / "assets" / "minecraft" / "atlases"
    atlas_dir.mkdir(parents=True, exist_ok=True)
    (atlas_dir / "blocks.json").write_text(
        json.dumps(
            {
                "sources": [
                    {
                        "type": "minecraft:directory",
                        "source": "galaxy:item",
                        "prefix": "item/",
                    },
                    {
                        "type": "minecraft:directory",
                        "source": "galaxy:source",
                        "prefix": "source/",
                    }
                ]
            },
            indent=2,
        ) + "\n",
        encoding="utf-8",
    )

    item_dir = output_dir / "assets" / "galaxy" / "items"
    model_dir = output_dir / "assets" / "galaxy" / "models" / "item"
    standalone_texture_dir = output_dir / "assets" / "galaxy" / "textures" / "item" / "source"
    item_dir.mkdir(parents=True, exist_ok=True)
    model_dir.mkdir(parents=True, exist_ok=True)

    models_found = set()
    model_sources = sorted(source_dir.rglob("*.bbmodel"))
    if not model_sources:
        fail(f"No Blockbench models found in {source_dir}.")

    loose_textures = {}
    for image_path in sorted(source_dir.rglob("*.png")):
        relative_path = image_path.relative_to(source_dir)
        safe_relative = Path(
            *[slug(part) + image_path.suffix.lower() if part == relative_path.name
              else slug(part) for part in relative_path.parts]
        )
        destination = standalone_texture_dir / safe_relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(image_path.read_bytes())
        resource_id = "galaxy:item/source/" + safe_relative.with_suffix("").as_posix()
        loose_textures[slug(relative_path.as_posix())] = resource_id
        basename = slug(relative_path.name)
        if basename in loose_textures and loose_textures[basename] != resource_id:
            fail(f"Loose texture basename is ambiguous: {relative_path.name}")
        loose_textures[basename] = resource_id
    for metadata_path in sorted(source_dir.rglob("*.png.mcmeta")):
        relative_path = metadata_path.relative_to(source_dir)
        destination = standalone_texture_dir / relative_path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(metadata_path.read_bytes())

    models_without_exports = []
    for source_path in model_sources:
        model_id = slug(source_path.relative_to(source_dir).as_posix())
        if model_id in models_found:
            fail(f"More than one source file maps to item model id {model_id!r}.")
        source_model = read_json(source_path)
        texture_dir = output_dir / "assets" / "galaxy" / "textures" / "item" / model_id
        texture_dir.mkdir(parents=True, exist_ok=True)
        textures_by_index, textures_by_name = extract_textures(source_path, source_model, texture_dir)

        exported_path = source_path.with_suffix(".json")
        if not exported_path.exists():
            models_without_exports.append(source_path.relative_to(source_dir).as_posix())
            continue
        exported = repair_export_textures(source_model, read_json(exported_path))
        output_model = rewrite_exported_model(
            exported_path, exported, textures_by_name, model_id
        )
        if model_id.startswith("celestial_bow_pulling_"):
            compensate_drawn_arm(output_model)
        if model_id in FIRST_PERSON_OVERRIDES:
            # The authored first-person values push these models out of view.
            displays = output_model.setdefault("display", {})
            transform = FIRST_PERSON_OVERRIDES[model_id]
            displays["firstperson_righthand"] = {key: list(value) for key, value in transform.items()}
            displays["firstperson_lefthand"] = {key: list(value) for key, value in transform.items()}
        shield_blocking = None
        if model_id == "nova_shield" and source_model.get("display"):
            # The shield's display values are authored in the .bbmodel; its .json export can be stale.
            output_model["display"] = {
                key: dict(value) for key, value in source_model["display"].items()
            }
            for key in ("thirdperson_righthand", "thirdperson_lefthand"):
                transform = output_model["display"].get(key)
                if transform:
                    scale = transform.get("scale", [1, 1, 1])
                    transform["scale"] = [round(axis * 1.15, 4) for axis in scale]
            output_model = align_shield_offhand(output_model)
            shield_blocking = build_shield_blocking(output_model)

        model_output = model_dir / f"{model_id}.json"
        item_output = item_dir / f"{model_id}.json"
        model_output.parent.mkdir(parents=True, exist_ok=True)
        item_output.parent.mkdir(parents=True, exist_ok=True)
        model_output.write_text(
            json.dumps(output_model, indent=2) + "\n", encoding="utf-8"
        )
        item_output.write_text(
            json.dumps(
                {
                    "model": {
                        "type": "minecraft:model",
                        "model": f"galaxy:item/{model_id}",
                    }
                } if shield_blocking is None else {
                    "model": {
                        "type": "minecraft:condition",
                        "property": "minecraft:using_item",
                        "on_false": {"type": "minecraft:model", "model": f"galaxy:item/{model_id}"},
                        "on_true": {"type": "minecraft:model", "model": f"galaxy:item/{model_id}_blocking"},
                    }
                },
                indent=2,
            ) + "\n",
            encoding="utf-8",
        )
        if shield_blocking is not None:
            (model_dir / f"{model_id}_blocking.json").write_text(
                json.dumps(shield_blocking, indent=2) + "\n", encoding="utf-8"
            )
        models_found.add(model_id)



    bbmodel_exports = {
        source.with_suffix(".json").resolve()
        for source in model_sources
    }
    for exported_path in sorted(source_dir.rglob("*.json")):
        if exported_path.resolve() in bbmodel_exports:
            continue
        model_id = slug(exported_path.relative_to(source_dir).as_posix())
        if model_id in models_found:
            fail(f"More than one source file maps to item model id {model_id!r}.")
        exported = read_json(exported_path)
        if not (
            isinstance(exported.get("elements"), list)
            or isinstance(exported.get("parent"), str)
        ) or not isinstance(exported.get("textures"), dict):
            fail(f"{exported_path.name}: JSON file is not a supported item model.")
        output_model = rewrite_exported_model(
            exported_path, exported, {}, model_id, loose_textures
        )
        model_output = model_dir / f"{model_id}.json"
        item_output = item_dir / f"{model_id}.json"
        model_output.parent.mkdir(parents=True, exist_ok=True)
        item_output.parent.mkdir(parents=True, exist_ok=True)
        model_output.write_text(
            json.dumps(output_model, indent=2) + "\n", encoding="utf-8"
        )
        item_output.write_text(
            json.dumps(
                {
                    "model": {
                        "type": "minecraft:model",
                        "model": f"galaxy:item/{model_id}",
                    }
                },
                indent=2,
            ) + "\n",
            encoding="utf-8",
        )
        models_found.add(model_id)

    write_boots_equipment(output_dir)

    for model_id in sorted(models_found):
        model_path = model_dir / f"{model_id}.json"
        item_path = item_dir / f"{model_id}.json"
        model = read_json(model_path)
        item = read_json(item_path)
        item_model = item.get("model", {})
        if item_model.get("type") == "minecraft:condition":
            item_model = item_model.get("on_false", {})
        if item_model.get("model") != f"galaxy:item/{model_id}":
            fail(f"{item_path}: item definition does not point to its generated model.")
        for texture in model.get("textures", {}).values():
            if not isinstance(texture, str) or texture.startswith("#"):
                continue
            if ":" not in texture:
                fail(f"{model_path}: texture reference is missing a namespace: {texture!r}.")
            namespace, texture_path = texture.split(":", 1)
            if namespace == "galaxy":
                png_path = output_dir / "assets" / namespace / "textures" / f"{texture_path}.png"
                if not png_path.is_file():
                    fail(f"{model_path}: missing referenced texture {texture}.")

    pulling_ids = [f"celestial_bow_pulling_{index}" for index in range(3)]
    if "celestial_bow" in models_found and all(model in models_found for model in pulling_ids):
        def bow_model(model_id):
            return {"type": "minecraft:model", "model": f"galaxy:item/{model_id}"}

        (item_dir / "celestial_bow.json").write_text(
            json.dumps(
                {
                    "model": {
                        "type": "minecraft:condition",
                        "property": "minecraft:using_item",
                        "on_false": bow_model("celestial_bow"),
                        "on_true": {
                            "type": "minecraft:range_dispatch",
                            "property": "minecraft:use_duration",
                            "scale": 0.05,
                            "fallback": bow_model("celestial_bow_pulling_0"),
                            "entries": [
                                {"threshold": 0.65, "model": bow_model("celestial_bow_pulling_1")},
                                {"threshold": 0.9, "model": bow_model("celestial_bow_pulling_2")},
                            ],
                        },
                    }
                },
                indent=2,
            ) + "\n",
            encoding="utf-8",
        )

    print(
        f"Generated Minecraft {PACK_FORMAT[0]}.{PACK_FORMAT[1]} pack; extracted textures "
        f"from all {len(model_sources)} Blockbench model(s); models included: "
        + ", ".join(sorted(models_found))
    )
    if models_without_exports:
        print("Skipped geometry conversion (no exported JSON): " + ", ".join(models_without_exports))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        fail("Usage: build-resource-pack.py <Textures directory> <output directory>")
    main(sys.argv[1], sys.argv[2])
