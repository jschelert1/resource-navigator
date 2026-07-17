# Resource Navigator Developer Guide

This document provides a high-level overview of the Resource Navigator architecture, major subsystems, design philosophy, and coding conventions. It is intended for developers maintaining or extending the plugin.

The project is intentionally organized as a collection of small, well-defined components with minimal coupling. Each subsystem is responsible for one stage of the resource navigation pipeline.

---

# Design Goals

The primary goals of Resource Navigator are:

- Treat Python resource strings as first-class IDE navigation targets.
- Integrate naturally with existing PyCharm navigation workflows.
- Avoid false-positive recognition of ordinary string literals.
- Keep recognition, resolution, navigation, and UI concerns separated.
- Favor maintainable, extensible, and testable code over special-case logic.
- Use only public IntelliJ Platform and PyCharm extension points whenever practical.

---

# High-Level Architecture

Resource Navigator processes a Python string through a staged pipeline:

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

Each stage performs one well-defined task before passing the result to the next stage.

---

# Package Organization

```text
actions/
```

Editor actions such as:

- Open Resource
- Reveal in File Manager
- Copy Full Path

---

```text
config/
```

Persistent user settings and configuration UI.

Examples:

- ResourceNavigatorSettings
- ResourceNavigatorConfigurable

---

```text
inspection/
```

Static analysis and diagnostics.

Examples:

- MissingResourceInspection

---

```text
navigation/
```

Navigation targets, PSI references, dispatcher, and open modes.

---

```text
preview/
```

Quick Documentation and future preview functionality.

---

```text
util/
```

Shared domain model and helper classes.

Examples:

- PythonResourceContext
- PythonStringUtil
- ResourceClassifier
- ResourceExtensionRegistry
- ResourceResolver
- ResourceTarget

---

# Core Components

## PythonResourceContext

Determines the semantic context of a Python string.

Examples include:

- pathlib constructors
- keyword arguments
- assignment fields

---

## PythonStringUtil

Provides reusable operations for Python string literals.

Responsibilities include:

- extracting string contents
- determining content ranges
- handling raw-string prefixes
- handling single and triple quotes

This class intentionally contains no resource-specific logic.

---

## ResourceClassifier

Determines whether a string should be treated as a navigable resource.

The classifier intentionally remains conservative to minimize false positives.

---

## ResourceResolver

Converts recognized resource strings into resolved filesystem or URL targets.

Responsibilities include:

- relative paths
- project-root resolution
- absolute paths
- URL handling

Python string parsing is delegated to PythonStringUtil.

---

## ResourceExtensionRegistry

Central registry for resource extension metadata.

Responsibilities include:

- recognized extensions
- browser-supported extensions
- external application mappings
- extension normalization
- default extension list

All extension-specific behavior should originate from this class.

---

## ResourceNavigationTargetFactory

Creates IDE navigation targets from resolved resources.

The factory determines which navigation choices should be presented to the user, such as:

- Open in PyCharm
- Open in Browser
- Open Externally

---

## ResourceDispatcher

Executes the selected navigation action.

Depending on the resource type, it opens the resource in:

- the IDE
- the system browser
- the operating system's associated application

---

# Architectural Rules

The following rules are intended to keep responsibilities clearly separated.

### ResourceClassifier

Responsible for:

- deciding whether something is a resource

Not responsible for:

- path resolution
- opening resources

---

### ResourceResolver

Responsible for:

- locating resources

Not responsible for:

- deciding whether a string is a resource
- creating navigation targets
- opening resources

---

### ResourceExtensionRegistry

Responsible for:

- extension metadata
- navigation labels
- extension normalization

Extension information should exist in only one location within the project.

---

### ResourceNavigationTargetFactory

Responsible for:

- constructing navigation targets

Not responsible for:

- resource recognition
- path resolution
- opening resources

---

### ResourceDispatcher

Responsible only for executing navigation.

It should not duplicate resource classification or extension metadata.

---

# Coding Guidelines

General conventions used throughout the project include:

- Small, single-purpose classes.
- Single responsibility per subsystem.
- Descriptive class and method names.
- Shared logic centralized whenever practical.
- Avoid duplicated extension metadata.
- Prefer immutable collections where appropriate.
- Keep UI code separated from navigation logic.
- Prefer composition over tightly coupled classes.

---

# Future Development

Planned areas of continued development include:

- richer image previews
- SVG thumbnail rendering
- additional Python syntax recognition
- improved dictionary semantics
- Alt-Enter quick fixes
- expanded automated testing
- Marketplace packaging
- performance optimization for large projects

---

# Source Documentation

This document intentionally provides only a high-level architectural overview.

Detailed implementation notes, revision history, and design rationale should remain close to the implementation within individual source files and class headers. This keeps the architecture documentation concise while allowing each subsystem to evolve independently.