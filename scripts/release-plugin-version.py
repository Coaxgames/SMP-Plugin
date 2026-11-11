import hashlib
import os
import re
import sys
from pathlib import Path
from zipfile import ZipFile


VERSION = re.compile(rb"(?m)^version:[ \t]*['\"]?(\d+\.\d+\.\d+)['\"]?[ \t]*(?=\r?$)")


def inspect_jar(path):
    with ZipFile(path) as jar:
        entries = [entry for entry in jar.infolist() if not entry.is_dir()]
        names = [entry.filename for entry in entries]
        if len(set(names)) != len(names) or "plugin.yml" not in names:
            raise ValueError(f"{path} has duplicate entries or no plugin.yml")
        metadata = jar.read("plugin.yml")
        matches = list(VERSION.finditer(metadata))
        if len(matches) != 1:
            raise ValueError(f"{path} must have exactly one numeric plugin version")
        version = matches[0].group(1).decode("ascii")
        digest = hashlib.sha256()
        for entry in sorted(entries, key=lambda value: value.filename):
            content = jar.read(entry)
            if entry.filename == "plugin.yml":
                content = VERSION.sub(b"version: '<version>'", content)
            name = entry.filename.encode("utf-8")
            digest.update(len(name).to_bytes(8, "big"))
            digest.update(name)
            digest.update(len(content).to_bytes(8, "big"))
            digest.update(content)
        return version, digest.hexdigest()


def select_plugin_version(candidate, previous):
    candidate_version, candidate_hash = inspect_jar(candidate)
    previous_version, previous_hash = inspect_jar(previous)
    return previous_version if candidate_hash == previous_hash else candidate_version


def main():
    if len(sys.argv) == 5 and sys.argv[1] == "--verify":
        candidate_version, candidate_hash = inspect_jar(Path(sys.argv[2]))
        final_version, final_hash = inspect_jar(Path(sys.argv[3]))
        if final_version != sys.argv[4] or final_hash != candidate_hash:
            raise ValueError(
                f"Final jar {final_version} differs from candidate {candidate_version} beyond its version"
            )
        print(f"Verified plugin {final_version}")
        return
    if len(sys.argv) != 4:
        raise ValueError("Expected candidate jar, previous jar, and plugin.yml paths, or --verify")
    candidate, previous, plugin_yml = map(Path, sys.argv[1:])
    version = select_plugin_version(candidate, previous)
    content = plugin_yml.read_bytes()
    updated, count = VERSION.subn(f"version: '{version}'".encode("ascii"), content)
    if count != 1:
        raise ValueError("plugin.yml must have exactly one numeric version")
    plugin_yml.write_bytes(updated)
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"plugin_version={version}\n")
    print(f"Plugin version: {version}")


if __name__ == "__main__":
    main()
