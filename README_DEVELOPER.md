# Resource Navigator Developer Guide

This document provides a high-level overview of the Resource Navigator architecture, major subsystems, execution paths, design philosophy, and coding conventions. It is intended for developers maintaining or extending the plugin.

The project is intentionally organized as a collection of small, well-defined components with minimal coupling. Each subsystem is responsible for one stage of resource recognition, compile-time evaluation, resolution, or navigation.

---

# Design Goals

The primary goals of Resource Navigator are:

* Treat Python resource strings as first-class IDE navigation targets.
* Support deterministic compile-time Python resource expressions.
* Integrate naturally with existing PyCharm navigation workflows.
* Preserve normal PyCharm declaration navigation for Python symbols.
* Avoid false-positive recognition of ordinary string literals.
* Keep evaluation, classification, resolution, navigation, and UI concerns separated.
* Favor maintainable, extensible, and testable code over special-case logic.
* Use only public IntelliJ Platform and PyCharm extension points whenever practical.

---

# High-Level Architecture

Version 2.0 separates resource processing into several distinct stages:

```text
Python Source
      │
      ▼
Python PSI
      │
      ▼
PythonResourceContext
      │
      ├── determines hyperlink source
      │
      └── determines evaluation scope
                  │
                  ▼
        PythonStringResolver
                  │
                  ▼
      PythonConstantStringEvaluator
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

This diagram represents the complete conceptual pipeline. In practice, not every component is invoked at the same time.

Resource Navigator has separate IDE-registration and user-navigation execution paths.

---

# Execution Paths

Resource Navigator participates in PyCharm through multiple execution paths. The two most important are:

1. PSI/reference discovery and hyperlink registration.
2. User-triggered resource navigation.

These paths share core evaluation and resolution components but are invoked for different reasons and at different times.

---

## PSI and Reference Discovery

When PyCharm loads, indexes, analyzes, or refreshes Python source, Resource Navigator participates through its registered IntelliJ extension points.

This path determines which Python string literals can participate in Resource Navigator navigation and creates the PSI references that allow PyCharm to display resource hyperlinks.

Conceptually:

```text
PyCharm loads / analyzes Python source
                │
                ▼
      Python PSI is constructed
                │
                ▼
 ResourceReferenceContributor
                │
                ▼
   PyStringLiteralExpression
                │
                ▼
     PythonStringResolver
                │
                ▼
     ResourceClassifier
                │
                ▼
       ResourceResolver
                │
                ▼
 ResourceReferenceLocal / URL
                │
                ▼
 Resource hyperlink becomes available
```

This path may be exercised during editor loading, PSI analysis, highlighting, indexing-related activity, or other IDE operations that request references.

Its purpose is not to open a resource.

Its purpose is to tell PyCharm:

> This portion of this Python string represents a navigable resource.

The original string literal remains the Resource Navigator hyperlink source even when the complete resource value is obtained by evaluating a larger Python expression.

For example:

```python
BASE_PATH = r"C:\Projects\App"

icon = BASE_PATH + r"\icons\info.svg"
```

Resource Navigator may evaluate:

```text
BASE_PATH + r"\icons\info.svg"
```

to determine the complete resource path, while the hyperlink remains attached to:

```text
r"\icons\info.svg"
```

This separation allows `BASE_PATH` to retain its normal PyCharm declaration-navigation behavior.

---

## User-Triggered Navigation

The navigation path begins when the user explicitly invokes navigation, such as Ctrl+Click or Go to Declaration.

Conceptually:

```text
User Ctrl+Clicks resource literal
                │
                ▼
       IntelliJ Navigation
                │
       ┌────────┴────────┐
       │                 │
       ▼                 ▼
ResourceReferenceLocal   ResourceGotoDeclarationHandler
       │                 │
       └────────┬────────┘
                │
                ▼
   PythonResourceContext
                │
                ▼
     PythonStringResolver
                │
                ▼
 PythonConstantStringEvaluator
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
 ResourceNavigationElement
                │
                ▼
           navigate()
                │
                ▼
       ResourceDispatcher
                │
                ▼
 Browser / PyCharm / External Application / File Manager
```

This path performs the work necessary to determine the complete resource represented by the clicked literal and dispatch it according to Resource Navigator navigation policy.

The important architectural distinction is:

```text
Reference discovery
    determines that navigation is available

User navigation
    determines what the resource means and opens it
```

The same evaluation and resolution services may therefore appear in both execution paths without those paths being equivalent.

---

# Compile-Time Evaluation Architecture

Version 2.0 introduces deterministic compile-time Python resource evaluation.

The evaluator supports four general capability tiers.

## Tier 1 — Direct String Literals

```python
manual = "docs/manual.pdf"
```

Ordinary and raw string literals are evaluated directly.

---

## Tier 2 — Adjacent String Literals

```python
manual = (
    "docs/"
    "manual.pdf"
)
```

Adjacent literal fragments are reconstructed into one semantic string while preserving their original source representation for highlighting.

---

## Tier 3 — Constant F-Strings

```python
VERSION = "v2.0"

manual = f"docs/{VERSION}/manual.pdf"
```

Constant f-string expressions are evaluated recursively when all required values can be determined statically.

---

## Tier 4 — Compile-Time Expressions

```python
BASE_PATH = r"C:\Projects\App"

icon = BASE_PATH + r"\icons\info.svg"

icon_path = Path(
    BASE_PATH + r"\icons\info.svg"
)
```

Tier 4 supports deterministic expression composition including:

* constant references
* binary `+` expressions
* binary `/` expressions
* parenthesized expressions
* supported pathlib constructors
* recursive constant evaluation
* circular-reference detection

Resource literals can participate on either side of supported binary expressions.

```python
INFO_SVG = r"\icons\info.svg"

icon = (
    r"C:\Projects\App" + INFO_SVG
)
```

Unsupported or runtime-dependent expressions terminate compile-time evaluation rather than being guessed.

---

# Evaluation Scope vs. Hyperlink Scope

A central v2.0 architectural rule is that the expression required to calculate a resource may be larger than the PSI element that should behave as the Resource Navigator hyperlink.

For example:

```python
BASE_PATH + r"\icons\info.svg"
            ^^^^^^^^^^^^^^^^^^
```

The complete binary expression is the **evaluation scope**.

The literal is the **hyperlink scope**.

This distinction preserves normal PyCharm behavior:

```text
BASE_PATH
    │
    └── normal PyCharm declaration navigation

r"\icons\info.svg"
    │
    └── Resource Navigator navigation
```

`PythonResourceContext` determines the supported enclosing expression required for evaluation without transferring Resource Navigator ownership to surrounding Python references.

---

# PythonResolvedString

`PythonResolvedString` preserves the distinction between source representation and semantic value.

Conceptually:

```text
PythonResolvedString
    │
    ├── sourceString
    │       original source-oriented representation
    │
    └── resolvedString
            reconstructed compile-time semantic value
```

This distinction is particularly important for multiline and adjacent string literals.

Resource resolution requires the reconstructed semantic value, while editor highlighting and navigation ranges must remain associated with the original Python source.

---

# Package Organization

```text
actions/
```

Editor actions such as:

* Open Resource
* Reveal in File Manager
* Copy Full Path

---

```text
config/
```

Persistent user settings and configuration UI.

Examples:

* ResourceNavigatorSettings
* ResourceNavigatorConfigurable

---

```text
inspection/
```

Static analysis and diagnostics.

Examples:

* MissingResourceInspection

---

```text
navigation/
```

Navigation handlers, PSI references, synthetic navigation elements, dispatcher, open modes, and resource citation parsing.

Examples:

* ResourceGotoDeclarationHandler
* ResourceReferenceContributor
* ResourceReferenceLocal
* ResourceReferenceUrl
* ResourceNavigationTargetFactory
* ResourceNavigationElement
* ResourceDispatcher
* ResourceCitationParser

---

```text
preview/
```

Quick Documentation and future preview functionality.

Examples:

* ResourceDocumentationProvider

---

```text
util/
```

Shared evaluation, classification, resolution, domain-model, and helper classes.

Examples:

* PythonResourceContext
* PythonStringUtil
* PythonStringResolver
* PythonResolvedString
* PythonConstantStringEvaluator
* ResourceClassifier
* ResourceExtensionRegistry
* ResourceResolver
* ResourceTarget

---

# Core Components

## PythonResourceContext

Determines the supported syntactic and evaluation context of a Python string literal.

Responsibilities include:

* recognizing supported pathlib constructors
* determining the enclosing compile-time evaluation expression
* expanding through supported `+` and `/` binary expressions
* preserving the original string literal as the hyperlink source

This class operates on Python PSI and does not perform resource classification or filesystem resolution.

---

## PythonStringUtil

Provides reusable source-oriented operations for Python string literals.

Responsibilities include:

* extracting string contents
* determining content ranges
* handling raw-string prefixes
* handling single and triple quotes
* preserving source-oriented information required for highlighting

This class intentionally contains no resource classification or filesystem logic.

---

## PythonStringResolver

Coordinates compile-time string resolution for a Python resource literal.

It determines the supported evaluation expression through `PythonResourceContext`, evaluates that expression, and returns a `PythonResolvedString`.

---

## PythonConstantStringEvaluator

Recursively evaluates supported deterministic Python expressions.

Responsibilities include:

* ordinary string literals
* adjacent string literals
* constant f-strings
* constant references
* binary `+` and `/` expressions
* parenthesized expressions
* supported pathlib constructors
* circular-reference detection

Unsupported or runtime-dependent expressions return no compile-time result.

---

## ResourceClassifier

Determines whether a resolved semantic string should be treated as a navigable resource.

Responsibilities include:

* URL classification
* pathlib-context classification
* recognized-extension filtering
* path-like value heuristics
* absolute Windows, UNC, and Unix path recognition
* glob-pattern rejection
* missing-resource inspection filtering

The classifier performs no filesystem access.

---

## ResourceResolver

Converts a classified resource value into a resolved filesystem or URL target.

Responsibilities include:

* relative path resolution
* project-root resolution
* absolute filesystem paths
* URL targets
* resource existence and target construction

Compile-time Python evaluation is delegated to the Python evaluation subsystem.

---

## ResourceExtensionRegistry

Central registry for resource extension metadata and navigation policy.

Responsibilities include:

* recognized extensions
* browser-oriented extensions
* IDE-oriented extensions
* external-application extensions
* navigation labels
* extension normalization
* default extension configuration

All extension-specific navigation policy should originate from this class.

---

## ResourceNavigationTargetFactory

Creates the navigation target appropriate for a resolved resource.

Examples include:

* Open in Browser
* Open in PyCharm
* Open in Word
* Open in Excel
* Open Externally
* Open Folder

Directories bypass extension-based policy and receive native-folder navigation.

Regular files receive the navigation mode selected by `ResourceExtensionRegistry`.

---

## ResourceDispatcher

Executes resource navigation.

Depending on the resource type and selected mode, it opens the resource in:

* PyCharm
* the system browser
* the operating system's associated application
* the native file manager

The dispatcher performs navigation only. It does not classify resources or define extension policy.

---

## ResourceCitationParser

Parses explicit bracketed resource citations embedded within Python string literals.

For example:

```text
See [paper.pdf] and [Some Paper [1996].pdf].
```

Each balanced outer citation can become an independently navigable resource while nested brackets within filenames remain supported.

---

# Navigation Policy

Resource navigation policy is centralized in `ResourceExtensionRegistry`.

Conceptually:

```text
Resolved Resource
       │
       ├── URL ------------------------> Browser
       │
       └── Local Resource
              │
              ├── Directory ----------> Native file manager
              │
              └── File
                    │
                    ▼
            Extension Registry
                    │
          ┌─────────┼─────────┐
          ▼         ▼         ▼
       Browser     IDE     External
```

Examples:

| Resource                       | Navigation                               |
|--------------------------------|------------------------------------------|
| HTML / HTM / MHTML             | Browser                                  |
| Python / JSON / text resources | PyCharm                                  |
| PDF                            | External application                     |
| Word documents                 | Microsoft Word / associated application  |
| Excel workbooks                | Microsoft Excel / associated application |
| Images                         | External application                     |
| Directories                    | Native file manager                      |
| Unregistered file types        | Operating-system associated application  |

IDE navigation is explicit rather than the fallback for arbitrary resource types.

---

# Architectural Rules

The following rules keep subsystem responsibilities separated.

### PythonResourceContext

Responsible for:

* Python PSI context
* supported evaluation scope

Not responsible for:

* compile-time evaluation
* resource classification
* filesystem resolution

---

### PythonConstantStringEvaluator

Responsible for:

* deterministic compile-time Python string evaluation

Not responsible for:

* deciding whether the result represents a resource
* filesystem resolution
* navigation

---

### ResourceClassifier

Responsible for:

* deciding whether a resolved semantic value is a resource candidate

Not responsible for:

* Python expression evaluation
* path resolution
* opening resources

---

### ResourceResolver

Responsible for:

* locating resources and constructing resolved targets

Not responsible for:

* deciding whether a value is a resource
* creating navigation targets
* opening resources

---

### ResourceExtensionRegistry

Responsible for:

* extension metadata
* navigation policy
* navigation labels
* extension normalization

Extension information should exist in only one location within the project.

---

### ResourceNavigationTargetFactory

Responsible for:

* constructing the appropriate navigation target

Not responsible for:

* resource recognition
* compile-time evaluation
* path resolution
* opening resources

---

### ResourceDispatcher

Responsible only for executing navigation.

It should not duplicate resource classification, compile-time evaluation, or extension metadata.

---

# Development Environment and Compatibility

Resource Navigator 2.0.1 is developed and built against:

* PyCharm 2026.2 (Build 262)
* JDK 25
* Kotlin 2.3.20
* IntelliJ Platform Gradle Plugin 2.18.1
* Gradle 9.6.1 (or compatible)

The minimum supported IDE platform is PyCharm 2025.2 (Build 252). No upper IDE compatibility bound is declared.

Compatibility is checked with IntelliJ Plugin Verifier against:

* PyCharm 2025.2.6.1 (Build 252)
* PyCharm 2026.2 (Build 262)

Run:

```text
verifyPlugin
```

---

## Experimental API Usage

IntelliJ Plugin Verifier currently reports five usages of experimental PyCharm Python APIs across four API methods:

* `PyAstBinaryExpression.getOperator()`
* `PyAstStringLiteralExpression.getStringValue()`
* `PyAstStringElement.getDecodedFragments()`
* `PyAstFormattedStringElement.getDecodedFragments()`

These APIs are currently compatible with both PyCharm 2025.2.6.1 and PyCharm 2026.2 but should be treated as potential compatibility points when upgrading to future PyCharm releases.

Future IDE migrations should run `verifyPlugin` before release and review any changes to these APIs before modifying the Python evaluation subsystem.

---

# Development Diagnostics

Development-time diagnostic output is controlled globally through:

```kotlin
ResourceNavigatorSettings.DIAGNOSTICS_ENABLED
```

---

# Coding Guidelines

General conventions used throughout the project include:

* Small, single-purpose classes.
* Single responsibility per subsystem.
* Descriptive class and method names.
* Shared logic centralized whenever practical.
* Avoid duplicated extension metadata.
* Prefer immutable collections where appropriate.
* Keep UI code separated from navigation logic.
* Preserve the distinction between source representation and semantic value.
* Preserve the distinction between hyperlink scope and evaluation scope.
* Prefer composition over tightly coupled classes.

---

# Future Development

Planned areas of continued development include:

* richer image previews
* SVG thumbnail rendering
* additional deterministic Python syntax recognition
* improved dictionary semantics
* Alt-Enter quick fixes
* expanded automated testing
* Marketplace packaging
* performance optimization for large projects

---

# Source Documentation

This document provides the architectural map and major execution paths for Resource Navigator.

Detailed implementation notes, revision history, and design rationale remain close to the implementation within individual source files and class headers. This keeps the developer guide focused on subsystem relationships and execution flow while allowing individual implementations to evolve independently.
