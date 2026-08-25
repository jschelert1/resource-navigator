"""
========================================================================
Resource Navigator - GitHub Issues #1 to #8
Automated Inspection Regression Test Data
========================================================================

Derived from the manually verified Resource Navigator regression corpus.

Purpose
-------
This file is loaded by IntelliJ Platform fixture tests. Every executable case
in this file is expected to produce NO Resource Navigator missing-resource
diagnostic.

Important
---------
Navigation-positive cases, deliberately missing resources, and machine-specific
existing paths from the manual regression corpus are intentionally excluded here.
Those require dedicated reference/navigation/documentation fixture assertions
rather than a blanket no-warning inspection assertion.

GitHub Issue Map
----------------
#1  Generic type annotations are not resources.
#2  Generic annotations on function declarations do not trigger RN diagnostics.
#3  Escaped backslash literals are not resources.
#4  Unquoted resource-looking prose is ignored; quoted-resource positive parsing
    is tested separately because it requires a target/asserted warning.
#5  Forward and outside-call pseudo-keyword references fail closed; positive
    previous-keyword resolution is tested separately as a reference test.
#6  Escaped double-backslash literals are rejected as resource candidates.
#7  Dynamic pathlib composition fails closed.
#8  Positive missing-resource behavior is tested directly in
    MissingResourceInspectionTest.kt because IntelliJ expected-highlighting markup
    cannot reliably evaluate the RN warning range for the resolved path expression.

========================================================================
"""

from pathlib import Path
from typing import Sequence


# ----------------------------------------------------------------------
# Issues #1/#2 - Generic type annotations must not be resources
# ----------------------------------------------------------------------

generic_list: list[list[str]] = []
generic_paths: list[Path] = []
generic_dict: dict[str, Path] = {}
generic_optional: Path | None = None
generic_tuple: tuple[str, str] = ("a", "b")
generic_nested: list[dict[str, list[int]]] = []


def _trim_blank_lines(lines: Sequence[str]) -> list[str]:
    start = 0
    end = len(lines)

    while start < end and not lines[start].strip():
        start += 1

    while end > start and not lines[end - 1].strip():
        end -= 1

    return list(lines[start:end])


# ----------------------------------------------------------------------
# Issues #3/#6 - Escaped backslashes must not become resources
# ----------------------------------------------------------------------

escaped_backslash = "\\"
escaped_double_backslash = "\\\\"
escaped_tab = "\t"
escaped_newline = "\n"


# ----------------------------------------------------------------------
# Issue #4 - Unquoted resource-looking prose remains ignored
# ----------------------------------------------------------------------

unquoted_resource_prose = (
    r"Open C:\Temp\RN_issue4_example.xlsx for details"
)


# ----------------------------------------------------------------------
# Issue #5 - Boundary cases must fail closed
# ----------------------------------------------------------------------

class SomeClass:
    def __init__(
        self,
        base_path: Path,
        file_path: Path,
    ) -> None:
        self.base_path = base_path
        self.file_path = file_path


keyword_reference_forward = SomeClass(
    file_path=base_path / "RN_issue5_forward.xlsx",
    base_path=Path(r"C:\Temp"),
)  # Expected RN behavior: ignored; forward pseudo-keyword reference unsupported.


keyword_reference_outside = (
    base_path / "RN_issue5_outside.xlsx"
)  # Expected RN behavior: ignored; pseudo-keyword scope does not escape the call.


# ----------------------------------------------------------------------
# Issue #7 - Dynamic pathlib.Path composition must fail closed
# ----------------------------------------------------------------------

def get_runtime_path() -> Path:
    return Path(r"C:\Temp")


runtime_base = get_runtime_path()

dynamic_mhtml = runtime_base / "link.mhtml"
dynamic_pdf = runtime_base / "link.pdf"


# ----------------------------------------------------------------------
# Issue #8 - Positive missing-resource diagnostic is tested in Kotlin
# ----------------------------------------------------------------------

# Issue #8 is intentionally omitted from this no-warning Python corpus.
# Its statically resolvable Path case must produce an RN missing-resource warning,
# so it is asserted directly by MissingResourceInspectionTest.kt rather than here.
# This keeps myFixture.checkHighlighting() strict for every executable case in
# this file and avoids unreliable IntelliJ expected-highlighting markup matching.