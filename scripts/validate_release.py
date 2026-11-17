"""Check the built mod's assets and packaging; uses only the Python standard library.

Usage: python scripts/validate_release.py build/libs/reterra-0.2.0+mc1.21.1.jar
       Add --minecraft-jar PATH to also check referenced vanilla models/textures.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile
import zlib


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def resource_path(identifier, kind, extension):
    namespace, _, path = identifier.partition(":")
    if not path:
        namespace, path = "minecraft", namespace
    return f"assets/{namespace}/{kind}/{path}.{extension}"


def walk(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def validate(jar_path, minecraft_path):
    with zipfile.ZipFile(jar_path) as jar:
        assert jar.testzip() is None, "Release JAR failed its CRC check"
        names = set(jar.namelist())
        assert len(names) == len(jar.namelist()), "Duplicate JAR entries"
        resources = {name: jar.read(name) for name in names if name.endswith((".json", ".png"))}
        if minecraft_path:
            with zipfile.ZipFile(minecraft_path) as vanilla:
                resources.update(
                    (name, vanilla.read(name))
                    for name in vanilla.namelist()
                    if name.startswith("assets/minecraft/") and name.endswith((".json", ".png"))
                )
        data = {
            name: json.loads(value, object_pairs_hook=unique_object)
            for name, value in resources.items()
            if name.endswith(".json") and name in names
        }
        metadata = data["fabric.mod.json"]
        assert metadata["id"] == "reterra"
        assert "${" not in metadata["version"], "Version placeholder was not expanded"
        assert metadata["icon"] in names, "Project icon is missing"
        assert {"minecraft", "fabricloader", "fabric-api", "java"} <= metadata["depends"].keys()
        for entries in metadata["entrypoints"].values():
            for entry in entries:
                assert entry.replace(".", "/") + ".class" in names, f"Missing entrypoint {entry}"
        for config in metadata["mixins"]:
            config = config if isinstance(config, str) else config["config"]
            mixins = data[config]
            for group in ("mixins", "client", "server"):
                for mixin in mixins.get(group, []):
                    path = (mixins["package"] + "." + mixin).replace(".", "/") + ".class"
                    assert path in names, f"Missing mixin {path}"
            if int(re.search(r"\d+", metadata["depends"]["minecraft"]).group()) < 26:
                assert mixins.get("refmap") in names, "Missing remapped mixin reference map"
        assert not any(
            re.search(r"(?:GameTests|RenderCheck|AssetGalleryScreen|PreviewCapture|PreviewInput|TestAccess|TestChecks)\$?.*\.class$", name)
            for name in names
        ), "Test-only classes are packaged in the release"
        assert not any(name.startswith(("docs/", ".tools/", "src/", "animation-world/", "dev/reterra/testmixin/")) for name in names)
        assert any(name.startswith("LICENSE") for name in names), "License is missing"

        def require(path):
            if path.startswith("assets/minecraft/") and not minecraft_path:
                return
            assert path in resources, f"Missing referenced asset: {path}"

        def model_textures(path, seen=None):
            seen = set() if seen is None else seen
            assert path not in seen, f"Cyclic model inheritance: {path}"
            seen.add(path)
            require(path)
            if path not in resources:
                return {}
            model = json.loads(resources[path])
            textures = {}
            parent = model.get("parent", "")
            if parent and "builtin/" not in parent:
                textures.update(model_textures(resource_path(parent, "models", "json"), seen))
            textures.update(model.get("textures", {}))
            for face in (node["texture"] for node in walk(model.get("elements", [])) if "texture" in node):
                value, aliases = face, set()
                while value.startswith("#"):
                    assert value not in aliases, f"Cyclic texture alias in {path}: {value}"
                    aliases.add(value)
                    assert value[1:] in textures, f"Undefined texture {value} in {path}"
                    value = textures[value[1:]]
                require(resource_path(value, "textures", "png"))
            for value in textures.values():
                if not value.startswith("#"):
                    require(resource_path(value, "textures", "png"))
            return textures

        models = [name for name in data if name.startswith("assets/reterra/models/")]
        for name in models:
            model_textures(name)
        target = re.search(r"\d+(?:\.\d+)+", metadata["depends"]["minecraft"]).group()
        modern_recipes = tuple(map(int, target.split("."))) >= (1, 21, 2)
        modern_items = tuple(map(int, target.split("."))) >= (1, 21, 4)
        if tuple(map(int, target.split("."))) >= (26, 3):
            for path, table in data.items():
                if path.startswith("data/reterra/loot_table/"):
                    for node in walk(table):
                        assert not {"functions", "conditions", "function"} & node.keys(), f"Legacy loot format in {path}"
                        assert not isinstance(node.get("condition"), str), f"Legacy loot predicate in {path}"

        if modern_items:
            for name in models:
                if "/models/item/" not in name:
                    continue
                definition = f"assets/reterra/items/{Path(name).name}"
                assert definition in data, f"Missing item definition: {definition}"
                for node in walk(data[definition]):
                    if node.get("type") == "minecraft:model":
                        require(resource_path(node["model"], "models", "json"))
        blocks = [name for name in data if name.startswith("assets/reterra/blockstates/")]
        for name in blocks:
            for node in walk(data[name]):
                if "model" in node:
                    require(resource_path(node["model"], "models", "json"))
        lang = data["assets/reterra/lang/en_us.json"]
        for name in models:
            if "/models/item/" in name:
                item = Path(name).stem
                assert any(f"{kind}.reterra.{item}" in lang for kind in ("item", "block")), name
        for source in (Path(__file__).resolve().parents[1] / "src/main/java").rglob("*.java"):
            for key in re.findall(r'Text\.translatable\("([a-zA-Z0-9_.]+)"\s*[,)]', source.read_text()):
                if ".reterra." in key:
                    assert key in lang, f"Missing translation {key} in {source.name}"

        pngs = [name for name in names if name.endswith(".png")]
        for name in pngs:
            image = resources[name]
            assert image[:8] == b"\x89PNG\r\n\x1a\n", f"Invalid PNG: {name}"
            offset = 8
            while offset < len(image):
                length = struct.unpack_from(">I", image, offset)[0]
                payload = image[offset + 4 : offset + 8 + length]
                crc = struct.unpack_from(">I", image, offset + 8 + length)[0]
                assert zlib.crc32(payload) == crc, f"Invalid PNG CRC: {name}"
                offset += length + 12
            expected = (128, 128) if name == metadata["icon"] else (16, 16)
            assert struct.unpack_from(">II", image, 16) == expected, f"Unexpected texture size: {name}"

        recipes = {Path(name).stem: value for name, value in data.items() if name.startswith("data/reterra/recipe/")}
        catalog = data["assets/reterra/field_recipes.json"]
        entries = {entry["id"].split(":")[-1]: entry for entry in catalog}
        assert len(entries) == len(catalog), "Duplicate recipe browser entries"
        for identifier, recipe in recipes.items():
            if recipe["type"] == "reterra:compact_soil":
                for feedstock in ("coarse_dirt", "gravel"):
                    entry = entries.get(identifier + "_" + feedstock)
                    assert entry is not None, f"Missing stacked soil variant: {feedstock}"
                    assert entry["result"] == {"id": "minecraft:dirt", "count": 4}
                    assert entry["grid"] == [
                        {"item": "minecraft:" + feedstock, "count": 4},
                        {"item": "reterra:organic_compost", "count": 1},
                    ]
                continue
            assert identifier in entries, f"Crafting recipe absent from browser: {identifier}"
            ingredients = recipe.get("key", {}).values() if "key" in recipe else recipe.get("ingredients", [])
            for ingredient in ingredients:
                if modern_recipes:
                    assert isinstance(ingredient, str), f"Legacy ingredient in {target}: {identifier}"
                else:
                    assert isinstance(ingredient, dict), f"Modern ingredient in {target}: {identifier}"
            if "result" in recipe:
                assert recipe["result"] == entries[identifier]["result"], f"Wrong browser result: {identifier}"
        for recipe in list(recipes.values()) + catalog:
            items = [node["item"] for node in walk(recipe) if "item" in node]
            if "result" in recipe:
                items.append(recipe["result"]["id"])
            for item in items:
                if item.startswith("reterra:"):
                    assert resource_path(item.replace("reterra:", "reterra:item/"), "models", "json") in names, f"Missing item model: {item}"
        return {
            "version": metadata["version"],
            "json_files": len(data),
            "models": len(models),
            "blockstates": len(blocks),
            "pngs": len(pngs),
            "recipes": len(recipes),
            "browser_entries": len(catalog),
            "vanilla_references_checked": minecraft_path is not None,
            "sha256": hashlib.sha256(jar_path.read_bytes()).hexdigest(),
        }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    parser.add_argument("--minecraft-jar", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = json.dumps(validate(args.jar, args.minecraft_jar), indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report + "\n", encoding="utf-8")
    print(report)
