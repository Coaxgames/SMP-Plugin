# Automated builds and releases

- Nobody pushes directly to `main`, owners included. Anyone pushing a branch in this repository gets a version bump committed to that branch, a build, and an automatically created or updated PR to `main`. Merging that PR triggers the release workflow, which builds the merged commit and publishes a GitHub Release. The release tag and title are the same `major.minor.patch` version; the plugin jar, separate versioned Minecraft 26.3 resource-pack ZIP, and its SHA-1 sidecar are attached, and GitHub generates the release notes. The release workflow does not push to `main`; the version already bumped in the PR is what gets released.
- Fork contributors must open a PR themselves; Actions in this repository cannot observe pushes made only in another repository. Their PR builds are run by trusted automation without exposing a write token to the build.
- The version is incremented from the highest of `build.gradle`, `plugin.yml`, and existing repository tags. If a PR has already bumped the version past the latest tag, that version is reused for its release.
- `main` requires two approving reviews; repository admins can bypass that rule for pull requests only. The release workflow also verifies two current approvals or one admin approval for non-admin merges, and fails if a commit reaches `main` without a merged PR.
- Contributor branch pushes are handled by a trusted `workflow_run` workflow. The initial push workflow is intentionally read-only; the trusted workflow updates branch metadata and creates PRs without executing contributor code with write permissions.

## Required repository settings

1. Set `main` as the default branch.
2. In **Settings → Actions → General → Workflow permissions**, allow GitHub Actions to create pull requests.
3. Create a branch ruleset for `main` (**Settings → Rules → Rulesets**) with:
   - Require a pull request before merging: 2 approvals, dismiss stale approvals on new commits.
   - Block force pushes and restrict deletions.
   - Bypass list: **Repository admins, "For pull requests only"**. Do not add roles with direct-push bypass; GitHub Actions cannot be added to bypass lists and does not need to be, because the release workflow never pushes to `main`.
   - After the first automated PR has reported it, also require the `galaxy/build` status check. The check does not appear in the picker until it has been posted once.

The workflows use the repository's `GITHUB_TOKEN`; no personal access token or owner username is needed.

## Publishing the item resource pack

`gradlew build` extracts embedded PNGs from every `.bbmodel` file in `Textures/`, adds flipbook animation metadata, and creates a separate Minecraft 26.3 resource-pack ZIP and SHA-1 sidecar. These are attached to the GitHub Release separately from the plugin JAR.

Automatic conversion of `.bbmodel` geometry to Minecraft model JSON is currently disabled for debugging. When a matching Blockbench-exported Java item `.json` exists, the generator uses it to create the pack's model and item definition. Without that export, the textures and animations are still packaged, but no model JSON or item definition is emitted for that source.

The pack targets Minecraft 26.3 (`min_format`/`max_format` 97.1). Python 3 is required to run the generator (`-PpythonExecutable=...` can select a different interpreter).
