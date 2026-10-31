import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { nextReleaseVersion, updateReleaseVersion } from "./release-version.mjs";

test("increments the current source version when there are no release tags", () => {
    assert.equal(nextReleaseVersion("0.1.0", "0.1.0", []), "0.1.1");
});

test("uses the source version as the baseline when there are no release tags", () => {
    assert.equal(nextReleaseVersion("0.1.1", "0.1.1", []), "0.1.2");
});

test("increments past the latest tag even when the source is behind it", () => {
    assert.equal(nextReleaseVersion("0.1.1", "0.1.1", ["0.1.1", "0.1.2"]), "0.1.3");
});

test("two releases from the same source get distinct versions", () => {
    const first = nextReleaseVersion("0.1.0", "0.1.0", []);
    assert.equal(nextReleaseVersion("0.1.0", "0.1.0", [first]), "0.1.2");
});

test("increments the newest version when the source is not pre-bumped", () => {
    assert.equal(nextReleaseVersion("0.1.0", "0.1.0", ["0.1.0", "0.2.4"]), "0.2.5");
});

test("supports v-prefixed tags and increments the maximum source version", () => {
    assert.equal(nextReleaseVersion("0.2.1", "0.2.0", ["v0.1.9"]), "0.2.2");
});

test("updates both Gradle and plugin metadata together", () => {
    const root = mkdtempSync(join(tmpdir(), "galaxy-release-version-"));
    try {
        writeFileSync(join(root, "build.gradle"), "version = '1.2.3'\n");
        const resources = join(root, "src", "main", "resources");
        mkdirSync(resources, { recursive: true });
        writeFileSync(join(resources, "plugin.yml"), "version: '1.2.3'\n");

        assert.equal(updateReleaseVersion(root, ["1.2.3"]), "1.2.4");
        assert.match(readFileSync(join(root, "build.gradle"), "utf8"), /version = '1\.2\.4'/);
        assert.match(readFileSync(join(resources, "plugin.yml"), "utf8"), /version: '1\.2\.4'/);
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
});

test("rejects mismatched version formats", () => {
    assert.throws(() => nextReleaseVersion("1.2", "1.2.0", []), /major\.minor\.patch/);
});
