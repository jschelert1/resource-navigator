# Resource Navigator

Resource Navigator is a PyCharm plugin that transforms Python string literals containing local files, project resources, and URLs into first-class IDE navigation targets.

The plugin integrates with **Go to Declaration (Ctrl+Click)**, **Quick Documentation**, **editor context actions**, and **resource inspections** so that images, PDFs, Office documents, source files, web pages, and other resources can be navigated as naturally as Python symbols.

---

# Version 1.1.0 Milestone

## Implemented

- Ctrl-click / Go to Declaration for recognized local resource strings.
- `pathlib.Path(...)`, `PurePath`, `PureWindowsPath`, and `PurePosixPath` recognition.
- Keyword argument and assignment field recognition.
- Relative resolution against the current Python file directory and project root.
- Absolute Windows, UNC, and platform-native path resolution.
- HTTP/HTTPS URL navigation.
- Multiple navigation targets where appropriate (IDE, browser, or external application).
- Editor context actions:
    - Open Resource
    - Reveal in File Manager
    - Copy Full Path
- Missing-resource inspection.
- Quick Documentation metadata for resources.
- Configurable resource field names.
- Configurable recognized resource extensions.

## Planned Version 1.2 Features

### Navigation

- Improved dictionary-key recognition (for example `{"logo": "images/logo.svg"}`).
- Multi-root and content-root resource resolution.
- Additional Python syntax recognition.

### Editor Integration

- Alt-Enter intention actions.
- File chooser quick fix for missing resources.

### Preview

- SVG thumbnail rendering.
- PNG/JPEG image previews.
- Richer Quick Documentation previews.

### Testing

- IntelliJ Platform test fixture integration.
- Expanded automated unit and integration test coverage.

---

# Supported Resource Types

Current built-in support includes:

- Images (`svg`, `png`, `jpg`, `jpeg`, `gif`, `webp`, `bmp`, `ico`)
- Markdown
- HTML / HTM / MHTML
- PDF
- Microsoft Office documents
- OpenDocument files
- Draw.io and Visio diagrams
- Python source files
- Text, CSV, JSON, XML, YAML, TOML
- HTTP / HTTPS URLs

---

# Development Environment

This project currently targets:

- PyCharm 2025.2.6.1 (Build 252)
- JDK 21
- Kotlin 2.3.20
- IntelliJ Platform Gradle Plugin 2.13.1
- Gradle 9.4.1 (or compatible)

---

# Build and Run

1. Open this directory as a Gradle project in IntelliJ IDEA or PyCharm Professional.
2. Select a JDK 21 Gradle JVM.
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

# Resource Recognition

A Python string is treated as a resource when at least one of the following conditions is true:

1. It belongs to a configured keyword argument or assignment (for example `image_file=...`).
2. It is the argument of `Path(...)`, `PurePath(...)`, or a related pathlib constructor.
3. It has a configured resource extension and appears to represent a filesystem path.
4. The optional "Accept Any Existing Path" mode is enabled.
5. It is an HTTP or HTTPS URL and URL navigation is enabled.

The default recognition rules are intentionally conservative to avoid treating ordinary string literals as navigable resources.

---

# Configuration

Open:

```text
Settings → Tools → Resource Navigator
```

Current configuration options include:

- Recognized resource field names.
- Recognized file extensions.
- Relative path resolution options.
- URL navigation.
- Existing-path recognition.
- Missing-resource inspection behavior.

---

# Architecture Overview

Resource Navigator processes resources through a staged navigation pipeline:

```text
Python String Literal
        │
        ▼
PythonResourceContext
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
IDE / Browser / External Application
```

Primary components include:

- **PythonResourceContext** — Determines keyword, assignment, and pathlib context.
- **ResourceClassifier** — Determines whether a string represents a navigable resource.
- **ResourceResolver** — Resolves local filesystem resources.
- **ResourceExtensionRegistry** — Central registry of recognized resource extensions and navigation behavior.
- **ResourceNavigationTargetFactory** — Creates IDE navigation targets.
- **ResourceDispatcher** — Opens resources in the IDE, browser, or external applications.
- **MissingResourceInspection** — Reports unresolved resources.
- **ResourceDocumentationProvider** — Supplies Quick Documentation metadata.
- **ResourceNavigatorSettings** — Persists user configuration.

---

# Project Status

Resource Navigator is currently an actively developed project.

The core navigation engine is fully operational and supports local files, project resources, and URLs throughout the PyCharm editor. Development is currently focused on richer previews, expanded Python syntax recognition, additional IDE integration features, and comprehensive automated testing.

The plugin is implemented using standard IntelliJ Platform extension points and public PyCharm PSI APIs to maximize compatibility with future IDE releases.