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

* identifying Python documentation/docstring string literals
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
* policy-driven missing-resource inspection filtering
* separation of permissive positive navigation from conservative negative inspection

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
* read-only lookup of IntelliJ's current VFS state during resource resolution

Compile-time Python evaluation is delegated to the Python evaluation subsystem. Resolution must not synchronously refresh the VFS because reference discovery and inspections may execute under IntelliJ read locks.

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
* documentation/docstring context
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

# GitHub-First Development and Release Workflow

The primary source-of-truth branch is GitHub `main`. When a GitHub-connected
assistant or other contributor makes changes directly to the repository, those
changes are committed remotely first. The local IntelliJ IDEA checkout is then
synchronized by pulling `origin/main`; it is not the required origin of
every new commit.

Before pulling, inspect and preserve uncommitted local changes. Review remote
commits, run the complete test suite, and build the plugin ZIP locally. If a
validation issue requires a correction, commit it to GitHub (or explicitly push
a local correction), resynchronize, and validate again. Do not create a
superfluous local commit merely to publish a release.

Release tags are created from the GitHub Releases interface **after** all
release source and documentation changes are committed. The tag must point to
the final validated release commit on `main`, not an earlier local working
state. Publish the release with the locally built ZIP attached. Avoid creating
the same tag separately in IntelliJ IDEA.

For the full procedure, including versioning, release ZIP verification,
publication, installation, and smoke tests, see
[README_DEVELOPER_COMMIT.md](README_DEVELOPER_COMMIT.md).

---

# Development Environment and Compatibility

The authoritative source-tree version is defined by the `version` property in `gradle.properties`; published versions are identified by the corresponding GitHub release and Git tag. This guide uses explicit version numbers only for historical architecture and compatibility context.

Resource Navigator is developed and built against:

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

# Automated Testing Architecture

## Plugin descriptor regression tests (introduced in v2.2.4)

`PluginDescriptorTest.kt` checks the plugin homepage URL, required dependencies,
resource bundle keys, inspection and settings localization declarations, and
actual action text and descriptions through IntelliJ's `ActionManager`.
Use the root `idea-plugin` URL attribute, not a child `url` element. Inspection
localization uses `key`/`bundle` and `groupKey`/`groupBundle`; configurable
localization uses `key`/`bundle`. Actions use the resource bundle and keys
`action.<action-id>.text` and `action.<action-id>.description`, not `%key`
attributes. Preserve the product name and use sentence capitalization for
inspection labels. See [JetBrains internationalization](https://plugins.jetbrains.com/docs/intellij/internationalization.html).

Run `./gradlew test --tests "*PluginDescriptorTest"` for focused validation,
then `./gradlew test` for the full suite. The homepage link in the installed
plugin UI still requires a manual acceptance test.


Resource Navigator uses both ordinary unit tests and IntelliJ Platform/PyCharm fixture tests. The automated suite exercises resource classification, inspection behavior, Python PSI reference contribution, compile-time path evaluation, and resolved navigation targets.

Run the complete automated suite with:

```text
./gradlew test
```

The current regression suite includes coverage for:

* resource classification behavior
* GitHub Issues #1–#14 regression coverage, including #12–#14 false-positive diagnostics
* Issue #9 missing-resource policy and false-positive classification cases
* Issue #10 bracketed-versus-quoted docstring resource behavior
* positive missing-resource diagnostics
* standalone existing-resource navigation
* static `pathlib.Path` composition
* Issue #5 previous-keyword pseudo-scope resolution
* Issue #5 forward-keyword fail-closed behavior
* Issue #5 outside-call pseudo-scope boundaries
* quoted PDF navigation
* quoted DOCX navigation
* quoted JPG navigation
* existing bare-filename navigation independent of missing-resource warning policy
* bracketed docstring navigation and missing-resource diagnostics
* quoted docstring resource suppression

The suite is intentionally expected to grow as regressions are encoded. The authoritative checkpoint is a successful complete `./gradlew test` run rather than a hard-coded test count.

---

## Inspection Fixtures vs. Navigation Fixtures

Inspection tests and navigation tests have different filesystem requirements.

Ordinary inspection tests can use normal IntelliJ fixture sources or shared files under:

```text
src/test/testData/
```

For example, the GitHub Issues #1–#10 inspection regression corpus can use Python fixture sources when the test is primarily verifying whether Resource Navigator emits or suppresses diagnostics.

Navigation integration tests are different. Resource Navigator resolves local resources through the actual filesystem. IntelliJ light fixtures created with `configureByText()` or `addFileToProject()` can live in the temporary IntelliJ VFS under locations such as:

```text
temp:///src/example.py
```

Those virtual paths are not equivalent to normal operating-system filesystem paths and cannot always be mapped through `java.nio.file.Path`.

Therefore filesystem-backed navigation tests should:

1. Create a real temporary directory with `java.nio.file.Files`.
2. Create the resource target on the real filesystem.
3. Write the Python fixture itself to the real filesystem.
4. Refresh and obtain the Python source through `LocalFileSystem`.
5. Configure the fixture from that real `VirtualFile`.
6. Place the editor caret at the resource literal.
7. Inspect and resolve the contributed Resource Navigator PSI reference.

Conceptually:

```text
Real temporary .py file
        │
        ▼
LocalFileSystem VirtualFile
        │
        ▼
PyStringLiteralExpression
        │
        ▼
ResourceReferenceContributor
        │
        ▼
ResourceReferenceLocal
        │
        ▼
ResourceResolver
        │
        ▼
ResourceNavigationTargetFactory
        │
        ▼
ResourceNavigationElement
```

This arrangement keeps automated navigation tests aligned with normal production filesystem semantics.

---

## Testing Synthetic Navigation Targets

`ResourceReferenceLocal.resolve()` intentionally returns a synthetic `ResourceNavigationElement` rather than the underlying target `PsiFile` or `PsiDirectory`.

This is an important part of Resource Navigator's navigation architecture. Returning the underlying PSI file directly could allow IntelliJ to bypass Resource Navigator's dispatch policy for resources that should open in a browser, external application, or native file manager.

Consequently, navigation tests must not assume:

```kotlin
reference.resolve()!!.containingFile
```

is the resource target. The synthetic navigation element remains associated with the source Python PSI.

Instead, positive navigation tests should verify that:

1. the Resource Navigator reference exists
2. `resolve()` returns a `ResourceNavigationElement`
3. the navigation element exposes the expected resolved resource location
4. the location matches the real filesystem target created by the test

The resolved location is exposed through `ResourceNavigationElement.getLocationString()` / Kotlin `locationString`.

This tests the same synthetic-target architecture used by production Ctrl+Click navigation without actually launching Excel, Word, a browser, an image viewer, or another external application during the automated test run.

---

## Test Responsibilities

The automated suite is intentionally divided by responsibility.

```text
ResourceClassifierTest
    │
    └── classification and low-level resource recognition

MissingResourceInspectionTest
    │
    └── no-false-positive inspection regression corpus

MissingResourcePositiveDiagnosticsTest
    │
    └── deliberately missing resources must produce diagnostics

ResourceReferenceNavigationTest
    │
    └── PSI reference contribution and resolved navigation targets
```

Manual IDE smoke testing remains useful for behavior that crosses process or operating-system boundaries, including:

* Ctrl+Click interaction in an installed/sandbox plugin
* browser dispatch
* Microsoft Office / associated-application dispatch
* image-viewer dispatch
* native file-manager navigation
* Quick Documentation presentation

Automated tests should verify deterministic plugin logic wherever possible; manual smoke tests should verify the final IDE and operating-system integration behavior.

### Missing-Resource Confidence Policy

Navigation and missing-resource inspection deliberately have different confidence requirements.

* **Conservative** — inferred non-bracketed missing resources remain silent.
* **Balanced** — the default; missing diagnostics require strong filesystem structure, while ambiguous bare filenames and shallow API-like fragments remain silent.
* **Aggressive** — after universal false-positive exclusions, missing-resource classification follows the ordinary PSI-aware resource classifier.

Universal inspection exclusions include known command-line switches, glob patterns, separator-only values, standalone textual escape/control fragments, leading-dot fragments, and unresolved-brace values.

Explicit bracketed resources are handled before inferred-resource policy classification. In Python docstrings this distinction is especially important: `[path]` is explicit Resource Navigator syntax, while quoted and ordinary path-like documentation text is ignored.

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
* continued expansion of automated integration and navigation-policy coverage
* Marketplace packaging
* performance optimization for large projects

---

# Source Documentation

This document provides the architectural map and major execution paths for Resource Navigator.

Detailed implementation notes, revision history, and design rationale remain close to the implementation within individual source files and class headers. This keeps the developer guide focused on subsystem relationships and execution flow while allowing individual implementations to evolve independently.

---

# Public Distribution, GitHub Pages, and Google Search Setup

This section documents the reusable deployment procedure established for
Resource Navigator in November 2026. It is applicable to another public
IntelliJ/PyCharm plugin repository by replacing the owner, repository,
plugin ID, version, and release ZIP filename.

## 1. Publish a public GitHub repository

1. Make the repository public under **Settings -> General -> Danger Zone ->
   Change repository visibility**, if it is intended to be publicly accessible.
2. Publish a meaningful `README.md` describing features, installation,
   compatibility, source, and release links.
3. Publish a versioned release ZIP under **Releases**. The release asset must
   be downloadable without authentication.
4. Optionally fill out the GitHub repository description, website URL,
   and topics, and the account profile bio. These help visitors identify the
   project, but do not guarantee Google indexing.

A GitHub user profile or repository being publicly reachable does not
guarantee that Google has indexed it. Search Console verification of the
GitHub Pages website also does **not** verify ownership of `github.com`.

## 2. Host the custom plugin repository with GitHub Pages

The repository uses **Settings -> Pages -> Build and deployment ->
Deploy from a branch**, with branch `main` and folder `/docs`.
GitHub Pages serves files committed in `docs/` at:

```text
https://jschelert1.github.io/resource-navigator/
```

For another repository, substitute:

```text
https://<github-user>.github.io/<repository>/
```

Place an IntelliJ-compatible custom plugin descriptor at
`docs/updatePlugins.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
  <plugin id="YOUR_PLUGIN_ID"
          url="https://github.com/OWNER/REPO/releases/download/vX.Y.Z/PLUGIN-X.Y.Z.zip"
          version="X.Y.Z">
    <name>Your Plugin Name</name>
    <description>Brief plugin description.</description>
    <vendor>Your Name</vendor>
    <idea-version since-build="252"/>
  </plugin>
</plugins>
```

Use the **actual** plugin ID from `src/main/resources/META-INF/plugin.xml`,
the actual published release ZIP URL, and an IDE compatibility value
appropriate for the plugin. Do not copy the sample values blindly.

After Pages deploys, open the descriptor URL in a browser and confirm it
serves XML rather than a 404 page or GitHub HTML. The XML file is a
**plugin repository feed**; it is not itself the ZIP and is not a
Google sitemap.

## 3. Enable automatic plugin update discovery in PyCharm

1. Open **Settings -> Plugins -> gear -> Manage Plugin Repositories**.
2. Add the **full XML descriptor URL**, not the GitHub repository URL
   or the GitHub Pages homepage URL.
3. Open the installed plugin's details and change **Updates from**
   from JetBrains Marketplace to the new custom repository if needed.
4. Confirm that PyCharm offers an update when the feed advertises a
   newer compatible version than the installed plugin.
5. Use **Update**, restart the IDE, and verify the installed version.

This extra **Updates from** selection mattered during the Resource Navigator
setup: merely adding the custom repository did not initially cause its
installed plugin to offer the new release. Update availability also depends
on IDE compatibility and a working release ZIP link.

For **every subsequent release**, publish the new GitHub Release and ZIP
first, then change the version and ZIP URL in `docs/updatePlugins.xml`
on `main`. Wait for GitHub Pages deployment and verify the public XML
and ZIP URL. This is a post-release metadata commit and must **not**
move or recreate the release tag. See `README_DEVELOPER_COMMIT.md`.

## 4. Create a public project homepage

The Resource Navigator Pages site contains:

```text
docs/index.html          Public landing page, title, description, canonical URL
docs/sitemap.xml         Google-compatible sitemap listing the homepage
docs/robots.txt          Crawler access rules and sitemap location
docs/updatePlugins.xml   JetBrains/PyCharm custom update feed
docs/google*.html        Google Search Console verification file, if supplied
```

A reusable homepage should explain what the project does, describe its
features, link to its source and releases, and include installation
instructions. Give it a descriptive HTML `<title>`, a meta description,
a canonical URL, and mobile-friendly markup.

GitHub Pages publishes `docs/index.html` as the repository Pages root.
Use an **absolute** canonical URL matching that public root. A minimal
sitemap looks like:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url><loc>https://OWNER.github.io/REPO/</loc></url>
</urlset>
```

A minimal `docs/robots.txt` is:

```text
User-agent: *
Allow: /

Sitemap: https://OWNER.github.io/REPO/sitemap.xml
```

The sitemap should list canonical, indexable website pages. It does not
need to list the plugin ZIP or `updatePlugins.xml`. For another project,
replace the owner/repository placeholders consistently.

## 5. Verify the Pages site in Google Search Console

1. Open `https://search.google.com/search-console/`.
2. Select **Add property -> URL prefix**. For Resource Navigator, the
   property is exactly:

   ```text
   https://jschelert1.github.io/resource-navigator/
   ```

3. Choose the **HTML file** ownership verification method and download
   the **exact file Google provides**. Its name and content are
   account/property-specific; do not reuse another site's token.
4. Commit that unmodified HTML file into `docs/` on the Pages branch.
   It must be publicly accessible at:

   ```text
   https://OWNER.github.io/REPO/googleXXXXXXXX.html
   ```

5. Wait for GitHub Pages deployment. Open the exact verification URL in
   a private browser session and check that the file is publicly reachable.
6. Return to Search Console and click **Verify**.

During the original setup, an initial verification attempt returned
**Could not find your site**. Retrying after the site/verification file
became publicly available succeeded. Check the URL prefix, exact
filename/content, Pages deployment status, and HTTP availability before
retrying. Do **not** delete the verification file after success;
Google may periodically recheck ownership.

The **HTML tag** verification method is another option, but it requires
placing the exact Search Console meta tag in the site's HTML. GitHub
Pages does not grant control of the `github.io` parent domain's DNS,
so domain-property verification is not the appropriate default for
a repository Pages URL.

## 6. Submit the sitemap and request indexing

After ownership verification:

1. Click **Go to property** in Search Console.
2. Open **Indexing -> Sitemaps**.
3. Under **Add a new sitemap**, enter `sitemap.xml` and click **Submit**.
4. Use **URL inspection** on the exact homepage URL.
5. When Google can access the page, choose **Request indexing**.
6. Check **Pages** and **Performance** over subsequent days/weeks for
   crawling, indexing, and search-impression information.

A newly created property may initially show **Processing data**.
Successful ownership verification, successful sitemap submission,
a crawl, indexing, and ranking are **distinct events**. Search Console
does not guarantee that a page will appear in Google results or
determine when it will rank.

Google Search Console cannot directly request indexing of the GitHub
profile or repository pages at `github.com/OWNER`, because the user
does not control that host. The verified Pages site can link prominently
to the GitHub repository and releases, improving discovery.

## 7. Maintenance checklist for another public repository

- [ ] Public repository and downloadable GitHub Release ZIP
- [ ] Correct plugin ID, version, ZIP URL, and IDE compatibility in update feed
- [ ] GitHub Pages configured to deploy `main` / `docs`
- [ ] Public `updatePlugins.xml` loads and PyCharm detects the custom feed
- [ ] Installed plugin's **Updates from** source points to the custom feed
- [ ] Public `index.html`, `sitemap.xml`, and `robots.txt` load
- [ ] Search Console URL-prefix property uses the exact Pages site root
- [ ] Google-issued verification file remains accessible under `docs/`
- [ ] Sitemap submitted and homepage inspected/requested for indexing
- [ ] Each future release updates the XML feed **after** the ZIP is published

