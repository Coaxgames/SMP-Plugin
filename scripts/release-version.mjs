import { appendFileSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

function parseVersion(value) {
    const match = /^v?(\d+)\.(\d+)\.(\d+)$/.exec(value.trim());
    return match ? match.slice(1).map(Number) : null;
}

function compareVersions(left, right) {
    for (let index = 0; index < left.length; index++) {
        if (left[index] !== right[index]) {
            return left[index] - right[index];
        }
    }
    return 0;
}

function formatVersion(version) {
    return version.join(".");
}

export function nextReleaseVersion(gradleVersion, pluginVersion, tags, preBumped = false) {
    const currentVersions = [gradleVersion, pluginVersion].map(parseVersion);
    if (currentVersions.some((version) => version === null)) {
        throw new Error("Gradle and plugin.yml versions must use major.minor.patch format.");
    }

    const sourceVersion = currentVersions.sort(compareVersions).at(-1);
    const releasedVersions = tags.map(parseVersion).filter((version) => version !== null);
    const latestReleased = releasedVersions.length === 0
        ? null
        : releasedVersions.sort(compareVersions).at(-1);

    if (preBumped && (latestReleased === null || compareVersions(sourceVersion, latestReleased) > 0)) {
        return formatVersion(sourceVersion);
    }

    const baseVersion = latestReleased !== null && compareVersions(latestReleased, sourceVersion) > 0
        ? latestReleased
        : sourceVersion;
    baseVersion[2] += 1;
    return formatVersion(baseVersion);
}

export function updateReleaseVersion(root, tags) {
    const gradlePath = resolve(root, "build.gradle");
    const pluginPath = resolve(root, "src/main/resources/plugin.yml");
    const gradle = readFileSync(gradlePath, "utf8");
    const plugin = readFileSync(pluginPath, "utf8");
    const gradlePattern = /^version\s*=\s*['"]([^'"]+)['"]\s*$/m;
    const pluginPattern = /^version:\s*['"]([^'"]+)['"]\s*$/m;
    const gradleMatch = gradle.match(gradlePattern);
    const pluginMatch = plugin.match(pluginPattern);

    if (!gradleMatch || !pluginMatch) {
        throw new Error("Could not find the Gradle and plugin.yml version declarations.");
    }

    const preBumped = process.env.PREBUMPED === "true";
    const version = nextReleaseVersion(gradleMatch[1], pluginMatch[1], tags, preBumped);
    writeFileSync(gradlePath, gradle.replace(gradlePattern, `version = '${version}'`));
    writeFileSync(pluginPath, plugin.replace(pluginPattern, `version: '${version}'`));
    return version;
}

function main() {
    const root = process.env.SOURCE_ROOT ?? process.cwd();
    const tags = (process.env.RELEASE_TAGS ?? "").split(/\r?\n/).filter(Boolean);
    const version = updateReleaseVersion(root, tags);
    const output = process.env.GITHUB_OUTPUT;
    if (output) {
        appendFileSync(output, `version=${version}${process.platform === "win32" ? "\r\n" : "\n"}`);
    }
    console.log(`Release version: ${version}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
    try {
        main();
    } catch (error) {
        console.error(error instanceof Error ? error.message : error);
        process.exitCode = 1;
    }
}
