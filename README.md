# Resource Navigator

Resource Navigator is a PyCharm plugin that transforms Python resource strings and compile-time resource expressions containing local files, project resources, and URLs into first-class IDE navigation targets.

The plugin integrates with **Go to Declaration (Ctrl+Click)**, **Quick Documentation**, **editor context actions**, and **resource inspections** so that images, PDFs, Office documents, source files, web pages, directories, and other resources can be navigated as naturally as Python symbols.

---

# Current Version

The authoritative source-tree version of Resource Navigator is the `version` property in `gradle.properties`. Published release versions are identified by the corresponding GitHub release and Git tag.

README and developer-documentation version numbers are retained only where they describe a historical milestone or the version in which a feature or architectural change was introduced; they are not intended to duplicate the current project version.

# Version 2.0.0 Milestone

Version 2.0 introduces compile-time Python resource evaluation, substantially expanding Resource Navigator beyond direct string-literal navigation.

Resource paths can now be reconstructed from adjacent literals, constant f-string expressions, constant references, binary string/path expressions, and supported `pathlib` constructors while preserving normal PyCharm declaration navigation for symbolic Python references.

## Implemented

* Ctrl-click / Go to Declaration for recognized local resource strings.
* Compile-time Python resource expression evaluation.
* Adjacent string-literal concatenation.
* Constant f-string interpolation.
* Recursive compile-time constant resolution.
* Binary `+` and `/` resource expressions.
* Parenthesized compile-time expressions.
* Circular-reference protection during constant evaluation.
* `pathlib.Path(...)`, `PurePath`, `PureWindowsPath`, and `PurePosixPath` recognition and evaluation.
* Preservation of the original resource literal as the Resource Navigator hyperlink while surrounding Python references continue to use normal PyCharm declaration navigation.
* Relative resolution against the current Python file directory and project root.
* Absolute Windows, UNC, and platform-native path resolution.
* Direct navigation to absolute directories and extensionless resources.
* HTTP/HTTPS URL navigation.
* Extension-based navigation policy for IDE, browser, and external applications.
* Native file-manager navigation for directories.
* Editor context actions:

    * Open Resource
    * Reveal in File Manager
    * Copy Full Path
* Missing-resource inspection with Conservative, Balanced, and Aggressive detection policies.
* Docstring-aware resource handling: bracketed citations are explicit resources; quoted and ordinary path-like documentation text is ignored.
* Quick Documentation metadata for resources.
* Configurable resource field names.
* Configurable recognized resource extensions.
* Configurable broad path-like value recognition.
* Glob-pattern detection to prevent unsupported wildcard references from becoming navigation links.
* Bracketed resource citations within Python string literals.
* Expanded and reorganized regression tests for supported and unsupported resource references.
* IntelliJ Platform automated regression testing for inspection, navigation, and missing-resource behavior.

---

# Compile-Time Resource Evaluation

Resource Navigator evaluates supported Python expressions when their values can be determined statically.

The evaluation model is organized into four capability tiers.

## Tier 1 — Direct String Literals

Ordinary and raw Python string literals are recognized directly.

```python
manual = "docs/manual.pdf"

image = r"C:\Projects\App\images\logo.png"
```

## Tier 2 — Adjacent String Literals

Adjacent Python literals are reconstructed into their complete semantic value.

```python
paper = (
    "docs/research/"
    "important-paper.pdf"
)
```

## Tier 3 — Constant F-Strings

F-strings containing compile-time constant expressions can be evaluated.

```python
VERSION = "v2.0"

manual = f"docs/{VERSION}/manual.pdf"
```

Escaped f-string braces remain supported as normal literal content.

## Tier 4 — Compile-Time Expressions

Resource Navigator recursively evaluates supported constant references, binary expressions, parentheses, and `pathlib` wrappers.

```python
BASE_PATH = r"C:\Projects\App"

icon = (
    BASE_PATH + r"\icons\info.svg"
)

icon_path = Path(
    BASE_PATH + r"\icons\info.svg"
)
```

Resource literals can participate on either side of a supported binary expression:

```python
INFO_SVG = r"\icons\info.svg"

icon = (
    r"C:\Projects\App" + INFO_SVG
)
```

The resource literal remains the Resource Navigator hyperlink, while symbolic components such as `BASE_PATH` and `INFO_SVG` retain their normal PyCharm declaration-navigation behavior.

Unsupported or dynamically evaluated Python expressions are left to normal PyCharm behavior rather than being guessed by Resource Navigator.

---

# Supported Resource Types

Current built-in support includes:

* Images (`svg`, `png`, `jpg`, `jpeg`, `gif`, `webp`, `bmp`, `ico`)
* Markdown
* HTML / HTM / MHTML
* PDF
* Microsoft Office documents
* OpenDocument files
* Draw.io and Visio diagrams
* Python source files and notebooks
* Text, CSV, TSV, JSON, XML, YAML, TOML
* HTTP / HTTPS URLs
* Absolute directories
* Absolute extensionless filesystem resources

---

# Resource Navigation Policy

Resource Navigator selects the preferred navigation mechanism according to the resource type.

| Resource                              | Default Navigation                       |
|---------------------------------------|------------------------------------------|
| HTML / HTM / MHTML                    | Browser                                  |
| PDF                                   | External application                     |
| Word documents                        | Microsoft Word / associated application  |
| Excel workbooks                       | Microsoft Excel / associated application |
| PowerPoint presentations              | PowerPoint / associated application      |
| Images                                | External application                     |
| Python / text / structured-data files | PyCharm                                  |
| Directories                           | Native file manager                      |
| HTTP / HTTPS URLs                     | Browser                                  |
| Unregistered file types               | Operating-system associated application  |

This keeps IDE navigation explicit rather than treating PyCharm as the fallback application for arbitrary resource types.

---

# Development Environment

This project currently uses:

* PyCharm 2026.2 (Build 262) as the development and build target
* PyCharm 2025.2 (Build 252) and later as the supported compatibility range
* JDK 25
* Kotlin 2.3.20
* IntelliJ Platform Gradle Plugin 2.18.1
* Gradle 9.6.1 (or compatible)

---

# Build and Run

1. Open this directory as a Gradle project in IntelliJ IDEA or PyCharm Professional.
2. Select a JDK 25 Gradle JVM.
3. Allow Gradle to download the PyCharm SDK and PythonCore dependency.
4. Run the Gradle task:

    ```text
    runIde
    ```

5. In the sandbox PyCharm instance, open `example.py` together with real files matching its resource paths.
6. Ctrl-click any recognized resource or URL.

To build an installable plugin ZIP:

```text
./gradlew buildPlugin
```

The resulting plugin is written to:

```text
build/distributions/
```

---

## Plugin Verification

Resource Navigator uses the IntelliJ Plugin Verifier to check compatibility with supported PyCharm versions.

The current release is verified against:

* PyCharm 2025.2.6.1 (Build 252)
* PyCharm 2026.2 (Build 262)

Run the Gradle task:

```text
verifyPlugin
```

---

## Automated Testing

Resource Navigator includes automated unit and IntelliJ Platform fixture tests for resource recognition, inspection, and navigation behavior.

Run the complete automated test suite with:

```text
./gradlew test
```

Current automated coverage includes:

* Resource classification and utility behavior.
* GitHub Issues #1–#10 regression coverage for false-positive, fail-closed, docstring, and missing-resource behavior.
* Issue #9 classification coverage for command-line switches, escape/control fragments, ambiguous filenames, API fragments, and strong filesystem paths.
* Issue #10 navigation and inspection coverage for bracketed versus quoted resources inside Python docstrings.
* Positive missing-resource diagnostics.
* Real-filesystem-backed PSI/reference navigation.
* Static `pathlib.Path` composition.
* Issue #5 previous-keyword resolution and forward/outside-call boundary behavior.
* Quoted PDF, DOCX, and JPG resource navigation.

Navigation integration tests use real temporary filesystem-backed Python sources and resource targets so the same filesystem-resolution semantics used by Resource Navigator in PyCharm are exercised by the test suite.

---

# Resource Recognition

A resolved Python value is treated as a resource candidate when supported Resource Navigator classification rules identify it as a URL or filesystem resource.

Resource candidates include:

1. Arguments to supported `Path(...)`, `PurePath(...)`, `PureWindowsPath(...)`, and `PurePosixPath(...)` constructors.
2. Values with configured resource extensions that resemble filesystem paths.
3. Absolute Windows, UNC, and platform-native filesystem paths regardless of filename extension.
4. Broader path-like values when **Accept Any Path-Like Value** is enabled.
5. HTTP or HTTPS URLs when URL navigation is enabled.

Compile-time resource expressions are evaluated before classification so that constants, f-strings, binary expressions, and other supported static forms can participate in the same resource-resolution pipeline.

Navigation and missing-resource inspection intentionally use different confidence rules. Existing path-like resources may remain navigable even when an equivalent missing value is too ambiguous to justify a diagnostic.

Inside Python docstrings, bracket syntax is the explicit Resource Navigator opt-in form: `[path]` may navigate or report a missing target, while quoted and ordinary path-like documentation text is ignored.

Glob patterns and unsupported dynamic expressions are intentionally excluded to minimize false-positive navigation.

---

# Configuration

Open:

```text
Settings → Tools → Resource Navigator
```

Current configuration options include:

* Recognized resource field names.
* Recognized file extensions.
* Relative path resolution options.
* URL navigation.
* Broad path-like value recognition.
* Missing-resource inspection enable/disable control.
* Missing-resource detection policy: Conservative, Balanced, or Aggressive.

---

# Architecture Overview

Resource Navigator processes Python resources through a staged evaluation and navigation pipeline:

```text
Python String Literal
        │
        ▼
PythonResourceContext
        │
        ▼
Compile-Time Evaluation Scope
        │
        ▼
PythonStringResolver
        │
        ▼
PythonResolvedString
        │
        ▼
ResourceClassifier
        │
        ▼
ResourceResolver
        │
        ▼
ResourceTarget
        │
        ▼
ResourceNavigationTargetFactory
        │
        ▼
ResourceDispatcher
        │
        ▼
IDE / Browser / External Application / File Manager
```

Primary components include:

* **PythonResourceContext** — Determines supported Python syntax context, docstring context, and compile-time evaluation scope.
* **PythonStringResolver** — Reconstructs supported compile-time Python resource expressions.
* **PythonResolvedString** — Preserves both source and resolved semantic string values.
* **PythonConstantStringEvaluator** — Recursively evaluates supported compile-time Python expressions and constants.
* **ResourceClassifier** — Determines whether a resolved semantic value represents a navigable resource.
* **ResourceResolver** — Resolves local filesystem resources and URLs.
* **ResourceExtensionRegistry** — Central registry of recognized resource extensions and preferred navigation behavior.
* **ResourceNavigationTargetFactory** — Creates the navigation target appropriate for the resolved resource.
* **ResourceDispatcher** — Opens resources in the IDE, browser, external application, or native file manager.
* **MissingResourceInspection** — Reports unresolved resource candidates.
* **ResourceDocumentationProvider** — Supplies Quick Documentation metadata.
* **ResourceNavigatorSettings** — Persists user configuration.

---

# Planned Features

### Navigation

* Improved dictionary-key recognition.
* Multi-root and content-root resource resolution.
* Additional Python syntax recognition where deterministic compile-time evaluation is possible.

### Editor Integration

* Alt-Enter intention actions.
* File chooser quick fix for missing resources.

### Preview

* SVG thumbnail rendering.
* PNG/JPEG image previews.
* Richer Quick Documentation previews.

### Testing

* Continued expansion of automated integration and navigation-policy coverage.

---

# Project Status

Resource Navigator is currently an actively developed project.

Version 2.0 establishes compile-time Python resource evaluation as a core part of the navigation engine. Resource Navigator can now reconstruct deterministic resource values from multiple Python expression forms while preserving normal PyCharm symbol-navigation behavior.

Development is currently focused on richer previews, additional deterministic Python syntax support, expanded IDE integration, and continued expansion of automated integration coverage.

The plugin is implemented using standard IntelliJ Platform extension points and PyCharm PSI APIs, with compatibility monitored through IntelliJ Plugin Verifier testing against supported IDE versions.
