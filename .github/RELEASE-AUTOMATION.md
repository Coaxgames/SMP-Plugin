# Automated builds and releases

- Nobody pushes directly to `main`, owners included. Contributors with write access push changes to the shared `UnStable` branch. Every push to `UnStable` builds that commit and creates or updates the single `UnStable` → `main` PR for review; pushes to other branches do not create automatic PRs. Merging that PR triggers the release workflow, which builds the merged commit and publishes a GitHub Release. The release tag and title are the same `major.minor.patch` version; the plugin jar, separate versioned Minecraft 26.3 resource-pack ZIP, and its SHA-1 sidecar are attached, and GitHub generates the release notes.
- Fork contributors must open a PR themselves; Actions in this repository cannot observe pushes made only in another repository. Their PR builds are run by trusted automation without exposing a write token to the build.
- Nobody edits the version. Every accepted PR gets a new revision: the workflow takes the highest source version or existing tag and increments its patch. The release tag, title, plugin JAR filename and metadata, and resource-pack filenames all use that same version.
- `build.gradle` and `plugin.yml` record the release version. The workflow commits them on `main` as `chore: release X.Y.Z`. Releases are serialized, so PRs merged back to back get distinct revisions. If `main` moves while a release is building, the release is still published on the built commit and the next release continues from the tags.
- The version commit is pushed by a dedicated GitHub App (see below), because the repository's `GITHUB_TOKEN` cannot bypass a branch ruleset. The app token is created only after the build finishes, so no build step can see it. The follow-up build/release run for that commit is detected and skipped.
- `main` requires two approving reviews from people other than the PR author, and admins are not exempt. The only exception is the repository owner, who is on the ruleset bypass list ("For pull requests only") and can merge a PR without the approvals; the release workflow mirrors that exemption by skipping its approval check when the repository owner performed the merge. For every other merge it re-verifies two current approvals, and it fails if a commit reaches `main` without a merged PR. PRs opened automatically by the workflow are authored by the bot, so any two collaborators can approve them.
- Pushes to `UnStable` are handled by a trusted `workflow_run` workflow. The initial push workflow is intentionally read-only; the trusted workflow creates or updates the PR without executing contributor code with write permissions.

## Required repository settings

1. Set `main` as the default branch.
2. In **Settings → Actions → General → Workflow permissions**, allow GitHub Actions to create pull requests.
3. Create a GitHub App for releases (**Settings → Developer settings → GitHub Apps → New GitHub App**, owned by the account or organization): no webhook, repository permission **Contents: Read and write**, installed only on this repository. Generate a private key, then in this repository's **Settings → Secrets and variables → Actions** add the variable `RELEASE_APP_ID` (the App ID) and the secret `RELEASE_APP_PRIVATE_KEY` (the full `.pem` contents).
4. Create a branch ruleset for `main` (**Settings → Rules → Rulesets**) with:
   - Require a pull request before merging: 2 approvals, dismiss stale approvals on new commits.
   - Block force pushes and restrict deletions.
   - Bypass list: the release App from step 3 with **Always** (it only pushes the `chore: release` version commit), and optionally the repository owner's user account with **For pull requests only**. Do not add the Repository admin role or any other role, so other admins need the same two approvals.
   - After the first automated PR has reported it, also require the `galaxy/build` status check. The check does not appear in the picker until it has been posted once.

Other workflows use the repository's `GITHUB_TOKEN`; no personal access token or owner username is needed.

## Publishing the item resource pack

`gradlew build` extracts embedded PNGs from every `.bbmodel` file in `Textures/`, adds flipbook animation metadata, and creates a separate Minecraft 26.3 resource-pack ZIP and SHA-1 sidecar. These are attached to the GitHub Release separately from the plugin JAR.

Automatic conversion of `.bbmodel` geometry to Minecraft model JSON is currently disabled for debugging. When a matching Blockbench-exported Java item `.json` exists, the generator uses it to create the pack's model and item definition. Without that export, the textures and animations are still packaged, but no model JSON or item definition is emitted for that source.

The pack targets Minecraft 26.3 (`min_format`/`max_format` 97.1). Python 3 is required to run the generator (`-PpythonExecutable=...` can select a different interpreter).

The updater verifies the latest plugin JAR and compares its contents with the installed JAR, ignoring only the `plugin.yml` version. It stages changed plugin code for restart; version-only changes do not restart the server. The pack service can send the latest pack live if the plugin contents match; otherwise it falls back to the installed plugin's release until restart. The running plugin's reported version cannot change without a restart, even though all published artifacts match. Existing servers must first install this updater and restart before version-only releases can avoid restarts.
