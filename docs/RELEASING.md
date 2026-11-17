# Releasing ReTerra

Pushing a tag such as `v0.9.0` runs the existing build and gameplay checks, verifies shared JAR groups, and creates a GitHub release with the resulting files. It does not publish to Modrinth.

## Create a release

1. Set `mod_version` in `gradle.properties` and add the release notes to `CHANGELOG.md`. Use a top-level heading such as `# ReTerra 0.9.0` for each version.
2. Commit the version change and workflow files, then push your branch.
3. Create and push a tag on that commit:

```powershell
git tag -a v0.9.0 -m "ReTerra 0.9.0"
git push origin v0.9.0
```

Replace `0.9.0` with the version you are releasing. The tag must exactly match `v` followed by `mod_version`. Creating a tag only on your computer does not start the workflow; pushing it to GitHub does.

Watch **Actions > Build, test and release**. Once all target jobs pass, the release appears under **Releases** with the shared and standalone JARs, `SHA256SUMS.txt` and a manifest listing each file's supported Minecraft versions. Release notes include that mapping and the matching changelog section.

Versions beginning with `0.` and versions with a suffix such as `-beta.1` are marked as GitHub prereleases. Versions such as `1.0.0` are normal releases.

## Later releases

Change `mod_version`, update the changelog, commit and push, then create a new tag. Minecraft targets come from `gradle/minecraft-targets.json`, shared release groups come from `gradle/release-groups.json`, and dependency versions remain in `versions/<version>/version.properties`. The workflow derives its test matrix and expected release files from these configurations.

The workflow uses GitHub's built-in token with write permission only in the release job; no personal access token is needed. Build artifacts are reused for publishing, so the release job does not rebuild the mod or repeat gameplay tests. GitHub's workflow only runs automatically for branch pushes and `v*` tags.

## Failed runs

A failed build or gameplay test prevents publication. Check the failed job's logs and test report artifact. If the failure was temporary, use GitHub's **Re-run failed jobs** on that tag's Actions run. An interrupted upload leaves a draft which a rerun can finish; an already published release is left unchanged.

If a code fix is needed, commit it and release a new version tag. Rerunning an old tag tests the original tagged commit, not your latest branch changes.
