# Resource Navigator Release Protocol

This document describes the complete workflow for creating and publishing a new
Resource Navigator release.

---

# 1. Complete Development

- Finish all code changes.
- Verify the plugin compiles without errors.
- Update regression tests.
- Run the complete automated test suite (`./gradlew test`) and verify all tests pass.
- Perform a quick manual validation in the development IDE.

---

# 2. Increment the Version

Edit:

```text
gradle.properties
```

Increment:

```properties
version=x.y.z
```

`gradle.properties` is the authoritative source-tree version. Do not duplicate the current version in README or developer-documentation headings merely to keep them synchronized. Explicit version numbers in documentation should be retained when they describe historical milestones, feature introductions, compatibility facts, or release examples.

After publication, the corresponding GitHub release and Git tag identify the authoritative public release version.

Example:

```properties
version=1.1.2
```

---

# 3. Update Documentation

Update release documentation directly on GitHub before the release tag is created. Changes made by an assistant or other GitHub-connected contributor are committed to `main` first; the local IntelliJ IDEA checkout then pulls those commits.

Typical files include:

- README.md
- README_DEVELOPER.md
- Screenshots
- Release notes
- Milestone/version references

README and developer documentation should reflect the release's behavior and architecture before the release tag is created. They do not need to repeat the current version when `gradle.properties` already supplies it.

---

# 4. Build the Plugin

Build the release plugin.

Example:

```
Build
    → Prepare Plugin Module 'Resource Navigator'
      For Deployment
```

or use the Gradle build task.

The release ZIP is generated in:

```text
build/distributions/
```

Example:

```text
resource-navigator-x.y.z.zip
```

Verify the plugin builds successfully before creating the release.

---

# 5. Synchronize, Test, and Build the Final Release Commit

> **GitHub-first workflow:** Source and documentation changes are authored and
> committed directly to GitHub `main`. IntelliJ IDEA is used to pull those
> commits, run validation, and generate the release ZIP. Do not create a release
> tag before the final source and documentation commits exist.

1. Confirm all proposed source, version, tests, and documentation edits have
   been committed to the GitHub `main` branch.
2. In IntelliJ IDEA, check for local uncommitted changes. Preserve or reconcile
   them before pulling; do not overwrite local work.
3. Pull `origin/main` and verify the local checkout is synchronized.
4. Run `./gradlew test`, then build the distributable ZIP in
   `build/distributions/`. Run `verifyPlugin` when compatibility validation
   is required.
5. Run the manual IDE smoke tests appropriate to the release.
6. If validation reveals a problem, make and commit the fix on GitHub (or
   intentionally commit and push a local fix), pull again, and repeat validation.
7. Confirm the ZIP version matches `gradle.properties` and corresponds to the
   final GitHub `main` commit.

Avoid an extra empty local commit simply to facilitate tagging. In this
workflow, the final release commit ordinarily already exists on GitHub.

---

# 6. Create the Git Release Tag on GitHub

After the final validated code and documentation commits are on `main`,
create the release tag through the GitHub Releases interface:

1. Open the repository's **Releases** page.
2. Select **Draft a new release**.
3. Choose **Choose a tag**, type `vX.Y.Z`, and select
   **Create new tag: vX.Y.Z on publish**.
4. Set the tag target to the intended final `main` commit.
5. Prepare release notes and the validated plugin ZIP.
6. Publish the release; GitHub creates the tag at publication.

For example, use `v2.2.2` for `version=2.2.2`.

**Do not** also create the same tag in IntelliJ IDEA or push a second copy.
If the tag already exists, select the existing tag rather than creating it
again. Once published, the release tag must not be moved. Never tag an earlier
commit that omits release documentation or fixes.

---

# Custom PyCharm Plugin Update Repository

Resource Navigator distributes updates through a JetBrains-compatible custom
plugin repository hosted by GitHub Pages, independently of JetBrains Marketplace.

Repository descriptor source:

```text
docs/updatePlugins.xml
```

Configure GitHub Pages once under **Settings -> Pages**, selecting **Deploy from
a branch**, branch `main`, folder `/docs`. Once Pages is active, the descriptor
should be accessible at:

```text
https://jschelert1.github.io/resource-navigator/updatePlugins.xml
```

In PyCharm, open **Settings -> Plugins -> gear -> Manage Plugin
Repositories**, remove any entry pointing to the GitHub repository homepage,
and add the descriptor URL above. PyCharm uses this feed to discover available
versions; GitHub Releases provides the plugin ZIP.

For each new release, after the release ZIP is published, update
`docs/updatePlugins.xml` with the new version and the exact release-asset URL.
Commit that descriptor update to `main` so GitHub Pages publishes it. Keep
the plugin ID consistent with `META-INF/plugin.xml`, and ensure the release
asset is publicly downloadable. Do not edit or move an already published
release tag to update this feed.

This repository descriptor is distribution metadata, not part of the tagged
plugin source. It may be updated after the release without retagging.
The GitHub Pages site must be enabled separately in repository settings.

---

# 7. Create GitHub Release

On GitHub:

```
Releases
    → Draft New Release
```

Select the tag created by GitHub when publishing (or select an existing tag if one was already created):

```text
vX.Y.Z
```

Release title:

```text
Resource Navigator vX.Y.Z
```

Attach:

```text
resource-navigator-x.y.z.zip
```

Before publishing, check that the selected target is the final validated `main`
commit and that the attached ZIP was built from that same source version.
Publishing creates the tag when **Create new tag on publish** was selected.

---

# 8. Install the New Plugin

## Remove Previous Version

```
Settings
    → Plugins
        → Resource Navigator
            → Uninstall
```

Restart PyCharm.

---

## Install New Version

```
Settings
    → Plugins
        → ⚙
            → Install Plugin from Disk...
```

Select:

```text
resource-navigator-x.y.z.zip
```

Click:

```
OK
```

Restart PyCharm.

---

# 9. Verify Installation

Confirm:

```
Settings
    → Plugins
        → Resource Navigator
```

Verify the displayed version matches the newly installed plugin.

---

# 10. Smoke Test

Verify:

- Ctrl+Click local file paths.
- Ctrl+Click URLs.
- Relative paths resolve correctly.
- Absolute Windows paths resolve correctly.
- `pathlib.Path(...)` references resolve correctly.
- Unsupported references (glob patterns, plain text, email addresses, version strings, etc.) do not become hyperlinks.
- Command-line switches and escape/control fragments do not produce missing-resource warnings.
- Quoted and ordinary path-like text inside Python docstrings is ignored.
- Bracketed resources inside Python docstrings navigate when present and warn when missing.
- Missing-resource Conservative, Balanced, and Aggressive policies behave as documented.
- The regression test file behaves as expected.

---

# Release Timeline

```text
Complete development / regression tests
        ↓
Commit source, version, and documentation directly on GitHub main
        ↓
Pull origin/main in IntelliJ IDEA
        ↓
Run automated tests and build release ZIP locally
        ↓
Perform IDE validation and verify plugin version
        ↓
If fixes are needed: commit to GitHub, pull, and validate again
        ↓
Draft GitHub Release targeting final main commit
        ↓
Attach release ZIP and publish
        ↓
GitHub creates vX.Y.Z tag on publish
        ↓
Install new plugin and smoke test
```

---

# Release Checklist

- [ ] Development and regression test updates complete
- [ ] `gradle.properties` version incremented
- [ ] README/developer documentation updated
- [ ] All release changes committed to GitHub `main`
- [ ] Local uncommitted changes reviewed and preserved
- [ ] IntelliJ IDEA pulled the final `origin/main` commit
- [ ] Automated test suite passed (`./gradlew test`)
- [ ] Plugin ZIP built successfully from the final source
- [ ] Plugin verifier run when applicable
- [ ] Local IDE validation and smoke tests passed
- [ ] Release target verified as the final `main` commit
- [ ] GitHub Release notes prepared and ZIP attached
- [ ] GitHub Release published and `vX.Y.Z` tag created or selected
- [ ] Release ZIP and plugin version verified
- [ ] New plugin installed and final smoke tests passed
