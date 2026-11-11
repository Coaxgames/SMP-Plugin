import importlib.util
import os
import subprocess
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile


SPEC = importlib.util.spec_from_file_location(
    "release_plugin_version", Path(__file__).with_name("release-plugin-version.py")
)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ReleasePluginVersionTests(unittest.TestCase):
    def test_only_version_change_keeps_previous_plugin_version(self):
        with TemporaryDirectory() as directory:
            previous = Path(directory) / "old.jar"
            candidate = Path(directory) / "new.jar"
            self.write_jar(previous, "2.22.30", b"same code")
            self.write_jar(candidate, "2.22.35", b"same code")
            self.assertEqual(MODULE.select_plugin_version(candidate, previous), "2.22.30")

    def test_changed_plugin_content_uses_new_version(self):
        with TemporaryDirectory() as directory:
            previous = Path(directory) / "old.jar"
            candidate = Path(directory) / "new.jar"
            self.write_jar(previous, "2.22.30", b"old code")
            self.write_jar(candidate, "2.22.35", b"new code")
            self.assertEqual(MODULE.select_plugin_version(candidate, previous), "2.22.35")

    def test_release_selection_and_rebuilt_jar_are_verified(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            previous, candidate, final = (root / name for name in ("old.jar", "candidate.jar", "final.jar"))
            self.write_jar(previous, "2.22.30", b"same code")
            self.write_jar(candidate, "2.22.35", b"same code")
            self.write_jar(final, "2.22.30", b"same code")
            metadata = root / "plugin.yml"
            metadata.write_text("name: GalaxySMP\nversion: '2.22.35'\nmain: example.Main\n", encoding="utf-8")
            output = root / "output"
            env = dict(os.environ, GITHUB_OUTPUT=str(output))
            script = str(Path(__file__).with_name("release-plugin-version.py"))
            subprocess.run([sys.executable, script, str(candidate), str(previous), str(metadata)],
                           env=env, check=True, capture_output=True)
            self.assertIn("plugin_version=2.22.30", output.read_text(encoding="utf-8"))
            self.assertEqual(metadata.read_text(encoding="utf-8"),
                             "name: GalaxySMP\nversion: '2.22.30'\nmain: example.Main\n")
            subprocess.run([sys.executable, script, "--verify", str(candidate), str(final), "2.22.30"],
                           check=True, capture_output=True)
            self.write_jar(final, "2.22.30", b"different code")
            result = subprocess.run([sys.executable, script, "--verify", str(candidate), str(final), "2.22.30"],
                                    capture_output=True)
            self.assertNotEqual(result.returncode, 0)

    @staticmethod
    def write_jar(path, version, code):
        with ZipFile(path, "w") as jar:
            jar.writestr("plugin.yml", f"name: GalaxySMP\nversion: '{version}'\n")
            jar.writestr("Galaxy.class", code)


if __name__ == "__main__":
    unittest.main()
