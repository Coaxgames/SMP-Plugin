# Automated builds and releases

- Admins pushing to `main` trigger a Java 25/Gradle build, version bump, and GitHub Release. The release tag and title are the same `major.minor.patch` version; the plugin jar, separate versioned Minecraft 26.3 resource-pack ZIP, and its SHA-1 sidecar are attached, and GitHub generates the release notes.
- A non-admin pushing a branch in this repository gets a version bump committed to that branch, a build, and an automatically created or updated PR to `main`. A merged PR triggers the release workflow.
- Fork contributors must open a PR themselves; Actions in this repository cannot observe pushes made only in another repository. Their PR builds are run by trusted automation without exposing a write token to the build.
- The version is incremented from the highest of `build.gradle`, `plugin.yml`, and existing repository tags. If a PR has already bumped the version past the latest tag, that version is reused for its release.
- Require two approving reviews on `main`; repository admins can bypass that rule. The release workflow also verifies two current approvals for non-admin merges.
- Contributor branch pushes are handled by a trusted `workflow_run` workflow. The initial push workflow is intentionally read-only; the trusted workflow updates branch metadata and creates PRs without executing contributor code with write permissions.

## Required repository settings

1. Set `main` as the default branch.
2. In **Settings → Actions → General → Workflow permissions**, allow GitHub Actions to create pull requests.
3. Keep the default `GITHUB_TOKEN` permission read-only where possible. Protect `main`, require two approving reviews (with admin bypass), and require these status checks before merging:
   - `galaxy/build`
4. Allow repository admins to bypass branch protection if they need to push directly to `main` or merge without the review threshold. Also allow the `github-actions[bot]` account to bypass the protected-branch rule for the automated version commit.

The workflows use the repository's `GITHUB_TOKEN`; no personal access token or owner username is needed.

## Publishing the item resource pack

`gradlew build` extracts embedded PNGs from the Java Block/Item `.bbmodel` files in `Textures/`, converts the models into Minecraft 26.3 item assets, packages `galaxy-smp-resource-pack-<version>.zip`, and writes a matching `.zip.sha1` file under `build/distributions/`. The release workflow attaches both files separately from the plugin jar. The pack targets Minecraft 26.3 (`min_format`/`max_format` 97.1).

To configure automatic server delivery after a release:

1. Copy the direct download URL for the resource-pack ZIP from its GitHub Release asset.
2. Copy the SHA-1 hex digest from the matching `.zip.sha1` release asset.
3. Add them to the server's `server.properties`:

   ```properties
   resource-pack=https://github.com/Coaxgames/SMP-Plugin/releases/download/<version>/galaxy-smp-resource-pack-<version>.zip
   resource-pack-sha1=<sha1-from-the-sidecar>
   ```

4. Restart the server. Players must accept the offered pack to see the custom models. For local client testing, download the ZIP and place it in `.minecraft/resourcepacks`.

The generator discovers every `.bbmodel` in `Textures/`. A matching Blockbench-exported Java item `.json` is used when available; otherwise the `.bbmodel` geometry is converted. All embedded PNGs and loose PNG/`.mcmeta` assets are included. Tall flipbook PNG strips receive animation `.mcmeta` with their Blockbench frame time. A `.json` is the exported model geometry; `.mcmeta` is separate Minecraft animation metadata. Texture flipbooks animate in the client, while bone/keyframe animation of model geometry is not supported by regular Minecraft item models. Python 3 is required to run the pack generator (`-PpythonExecutable=...` can select a different interpreter).
