# Automated builds and releases

- Admins pushing to `main` trigger a Java 25/Gradle build, version bump, and GitHub Release. The release tag and title are the same `major.minor.patch` version; the built plugin jar is attached and GitHub generates the release notes.
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
