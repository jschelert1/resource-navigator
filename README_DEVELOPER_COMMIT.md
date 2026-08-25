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

Example:

```properties
version=1.1.2
```

---

# 3. Update Documentation

Update any release documentation before creating the release commit.

Typical files include:

- README.md
- README_DEVELOPER.md
- Screenshots
- Release notes
- Milestone/version references

The README should reflect the new release version before the release tag is
created.

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

# 5. Create the Git Release Tag

> **IntelliJ IDEA workflow:** Create the release tag before opening the final
> Commit and Push dialog. IntelliJ IDEA allows the locally created tag to be
> included together with the selected release files during the final commit/push
> workflow. Therefore, Resource Navigator intentionally uses a tag-first release
> procedure. Do not reorder these steps to use a commit-first workflow.

Create a Git tag pointing to the current release.

Example:

```text
v1.1.2
```

The tag should represent the exact released source code.

Once published, the tag should never be moved.

---

# 6. Commit and Push

Commit all release changes together.

The release commit should contain:

- Source code
- Version increment
- README updates
- Developer documentation
- Any other release-related changes

Example commit message:

```text
Added glob pattern detection, reorganized regression tests, and improved handling of unsupported resource references.
```

Use:

```text
Commit and Push
```

Options:

```
✓ Push Tags
✗ Amend Last Commit
```

Because the tag already exists locally, both the release commit and the release
tag are pushed together.

---

# 7. Create GitHub Release

On GitHub:

```
Releases
    → Draft New Release
```

Select the existing tag:

```text
v1.1.2
```

Release title:

```text
Resource Navigator v1.1.2
```

Attach:

```text
resource-navigator-x.y.z.zip
```

Publish the release.

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
- The regression test file behaves as expected.

---

# Release Timeline

```
Complete development
        ↓
Run automated test suite
        ↓
Increment version
        ↓
Update README/documentation
        ↓
Build plugin ZIP
        ↓
Create Git release tag
        ↓
Commit and Push
        ↓
Create GitHub Release
        ↓
Upload release ZIP
        ↓
Install new plugin
        ↓
Smoke test
```

---

# Release Checklist

- [ ] Development complete
- [ ] Automated test suite passed (`./gradlew test`)
- [ ] Version incremented
- [ ] Documentation updated
- [ ] Plugin builds successfully
- [ ] Git tag created
- [ ] Commit and Push completed (Push Tags checked)
- [ ] GitHub Release published
- [ ] Release ZIP uploaded
- [ ] Plugin installed
- [ ] Smoke tests passed