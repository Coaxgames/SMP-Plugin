import base64
import json
import re
import shutil
import struct
import sys
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
    if loose_textures and key in loose_textures:
        return loose_textures[key]
    basename = slug(Path(name).name)
    if loose_textures and basename in loose_textures:
        return loose_textures[basename]
    if ":" in name:
        return name
    fail(f"{model_path.name}: exported model references missing texture {name!r}.")


def rewrite_exported_model(model_path, exported, textures_by_name, model_id, loose_textures=None):
    old_textures = exported.get("textures", {})
    new_textures = {}
    aliases = {
        f"#{key}": f"#{'t' + key if key.isdigit() else key}"
        for key in old_textures
    }
    for key, value in old_textures.items():
        alias = f"t{key}" if key.isdigit() else key
        if value.startswith("#"):
            new_textures[alias] = aliases.get(value, value)
        else:
            resolved = resolve_texture(value, textures_by_name, model_path, loose_textures)
            new_textures[alias] = (
                f"galaxy:item/{model_id}/{resolved}"
                if resolved.startswith("galaxy:item/")
                else resolved
            )

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
                face["texture"] = (
                    "galaxy:item/" + model_id + "/" + resolved
                    if resolved.startswith("galaxy:item/")
                    else resolved
                )
    return rewritten


def convert_bbmodel(model_path, model, textures_by_index, textures_by_name, model_id):
    if model.get("meta", {}).get("model_format") != "java_block":
        fail(f"{model_path.name}: expected a Java Block/Item Blockbench model.")
    if model.get("animations"):
        fail(
            f"{model_path.name}: Java item models cannot play Blockbench geometry "
            "animations; export static item geometry instead."
        )

    texture_aliases = {}
    new_textures = {}
    for index, texture in textures_by_index.items():
        alias = f"t{index}"
        texture_aliases[index] = "#" + alias
        new_textures[alias] = f"galaxy:item/{model_id}/{texture['filename'][:-4]}"
    particle_texture = next(
        (index for index, texture in enumerate(model.get("textures", [])) if texture.get("particle")),
        None,
    )
    if particle_texture is not None:
        new_textures["particle"] = f"#{particle_texture}"

    elements = []
    for source in model.get("elements", []):
        if not source.get("export", True):
            continue
        element = {
            "from": source["from"],
            "to": source["to"],
            "faces": {},
        }
        for face_name, source_face in source.get("faces", {}).items():
            texture_index = source_face.get("texture")
            if texture_index is None:
                continue
            texture_alias = texture_aliases.get(texture_index)
            if texture_alias is None:
                fail(f"{model_path.name}: face references missing texture index {texture_index}.")
            face = {
                "uv": source_face["uv"],
                "texture": texture_alias,
            }
            for key in ("rotation", "cullface", "tintindex"):
                if key in source_face:
                    face[key] = source_face[key]
            element["faces"][face_name] = face

        rotation = source.get("rotation")
        if rotation:
            if isinstance(rotation, list) and len(rotation) == 3:
                element["rotation"] = {
                    "x": rotation[0],
                    "y": rotation[1],
                    "z": rotation[2],
                    "origin": source.get("origin", [8, 8, 8]),
                    "rescale": bool(source.get("rescale", False)),
                }
            elif isinstance(rotation, dict):
                element["rotation"] = rotation
            else:
                fail(f"{model_path.name}: unsupported element rotation.")
        if "shade" in source:
            element["shade"] = source["shade"]
        elements.append(element)

    return {
        "format_version": "26.3",
        "credit": f"Converted from {model_path.name}.",
        "parent": model.get("parent", "minecraft:item/handheld"),
        "textures": new_textures,
        "elements": elements,
        "display": model.get("display", {}),
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

    item_dir = output_dir / "assets" / "galaxy" / "items"
    model_dir = output_dir / "assets" / "galaxy" / "models" / "item"
    standalone_texture_dir = output_dir / "assets" / "galaxy" / "textures" / "source"
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
        resource_id = "galaxy:source/" + safe_relative.with_suffix("").as_posix()
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

    for source_path in model_sources:
        model_id = slug(source_path.relative_to(source_dir).as_posix())
        if model_id in models_found:
            fail(f"More than one source file maps to item model id {model_id!r}.")
        source_model = read_json(source_path)
        texture_dir = output_dir / "assets" / "galaxy" / "textures" / "item" / model_id
        texture_dir.mkdir(parents=True, exist_ok=True)
        textures_by_index, textures_by_name = extract_textures(source_path, source_model, texture_dir)

        exported_path = source_path.with_suffix(".json")
        if exported_path.exists():
            exported = read_json(exported_path)
            output_model = rewrite_exported_model(
                exported_path, exported, textures_by_name, model_id
            )
        else:
            output_model = convert_bbmodel(
                source_path, source_model, textures_by_index, textures_by_name, model_id
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
        if not isinstance(exported.get("elements"), list) or not isinstance(
            exported.get("textures"), dict
        ):
            fail(f"{exported_path.name}: JSON file is not an exported item model.")
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

    for model_id in sorted(models_found):
        model_path = model_dir / f"{model_id}.json"
        item_path = item_dir / f"{model_id}.json"
        model = read_json(model_path)
        item = read_json(item_path)
        if item.get("model", {}).get("model") != f"galaxy:item/{model_id}":
            fail(f"{item_path}: item definition does not point to its generated model.")
        for texture in model.get("textures", {}).values():
            if not isinstance(texture, str) or texture.startswith("#") or ":" not in texture:
                continue
            namespace, texture_path = texture.split(":", 1)
            if namespace == "galaxy":
                png_path = output_dir / "assets" / namespace / "textures" / f"{texture_path}.png"
                if not png_path.is_file():
                    fail(f"{model_path}: missing referenced texture {texture}.")

    print(
        f"Generated Minecraft {PACK_FORMAT[0]}.{PACK_FORMAT[1]} pack from all "
        f"{len(model_sources)} Blockbench model(s), plus "
        + ", ".join(sorted(models_found))
    )


if __name__ == "__main__":
    if len(sys.argv) != 3:
        fail("Usage: build-resource-pack.py <Textures directory> <output directory>")
    main(sys.argv[1], sys.argv[2])
