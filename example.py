from pathlib import Path

# --------------------------------------------------
# Absolute file
# --------------------------------------------------

spreadsheet = r"C:\Research\Data\Results.xlsx"

# --------------------------------------------------
# Relative file
# --------------------------------------------------

icon = "icons/info.svg"

# --------------------------------------------------
# pathlib.Path
# --------------------------------------------------

paper = Path("papers/helix.pdf")

# --------------------------------------------------
# URL
# --------------------------------------------------

website = "https://www.jetbrains.com/pycharm/"


class Figure:
    """
    Demonstrates resource citations recognized by the plugin.

    Figure:
        [figures/helix.svg]

    Paper:
        [papers/helix.pdf]

    Spreadsheet:
        [C:\Research\Data\Results.xlsx]

    Website:
        https://www.jetbrains.com/pycharm/

    Bracketed URL:
        [https://plugins.jetbrains.com/]
    """

    def __init__(self, **kwargs):
        self.__dict__.update(kwargs)


helix = Figure(
    title="Two-turn Helix",
    image_file=Path("figures/helix.svg"),
    source_code=Path("plot_helix.py"),
    paper=Path("papers/helix.pdf"),
    url="https://www.jetbrains.com/pycharm/",
)