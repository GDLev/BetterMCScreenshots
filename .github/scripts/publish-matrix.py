#!/usr/bin/env python3
"""Builds the GitHub Actions matrix used by the publish workflow.

Every entry describes one release JAR: the Gradle project that produces it, the
mod loader it targets and the Minecraft versions it is published for. Both the
build and the publish job of `.github/workflows/publish.yml` consume this
matrix, so a new variant only has to be listed here once.

Environment:
    LOADERS   Loader to restrict the matrix to, or "all" (default).
    VARIANTS  Comma-separated list of variant paths to restrict the matrix to.
"""

import json
import os
import re
import sys
from pathlib import Path

LOADER_DISPLAY_NAMES = {
    "fabric": "Fabric",
    "neoforge": "NeoForge",
    "forge": "Forge",
}

# Gradle project -> Minecraft versions the resulting JAR supports.
VARIANTS = [
    ("fabric/1.21", ["1.21", "1.21.1"]),
    ("fabric/1.21.2", ["1.21.2", "1.21.3", "1.21.4"]),
    ("fabric/1.21.5", ["1.21.5"]),
    ("fabric/1.21.8", ["1.21.6", "1.21.7", "1.21.8"]),
    ("fabric/1.21.10", ["1.21.9", "1.21.10"]),
    ("fabric/1.21.11", ["1.21.11"]),
    ("fabric/26.1", ["26.1", "26.1.1", "26.1.2"]),
    ("fabric/26.2", ["26.2"]),
    ("fabric/26.3", ["26.3"]),

    ("neoforge/1.21", ["1.21", "1.21.1"]),
    ("neoforge/1.21.2", ["1.21.2", "1.21.3", "1.21.4"]),
    ("neoforge/1.21.5", ["1.21.5"]),
    ("neoforge/1.21.8", ["1.21.6", "1.21.7", "1.21.8"]),
    ("neoforge/1.21.10", ["1.21.9", "1.21.10"]),
    ("neoforge/1.21.11", ["1.21.11"]),
    ("neoforge/26.1", ["26.1", "26.1.1", "26.1.2"]),
    ("neoforge/26.2", ["26.2"]),

    ("forge/1.21", ["1.21", "1.21.1"]),
    ("forge/1.21.3", ["1.21.2", "1.21.3", "1.21.4"]),
    ("forge/1.21.5", ["1.21.5"]),
    ("forge/1.21.8", ["1.21.6", "1.21.7", "1.21.8"]),
    ("forge/1.21.10", ["1.21.9", "1.21.10"]),
    ("forge/1.21.11", ["1.21.11"]),
    ("forge/26.1", ["26.1", "26.1.1", "26.1.2"]),
    ("forge/26.2", ["26.2"]),
]


REPO_ROOT = Path(__file__).resolve().parents[2]
ALL_VARIANTS_PATTERN = re.compile(r"def allVariants\s*=\s*\[(.*?)\]", re.DOTALL)


def gradle_variants():
    """Returns the variants declared by build.gradle, or None when they cannot be read."""
    build_gradle = REPO_ROOT / "build.gradle"
    if not build_gradle.is_file():
        return None

    match = ALL_VARIANTS_PATTERN.search(build_gradle.read_text(encoding="utf-8"))
    return re.findall(r'"([^"]+)"', match.group(1)) if match else None


def main() -> None:
    """Writes the selected variant matrix to stdout as JSON."""
    loaders = (os.environ.get("LOADERS") or "all").strip()
    requested = [variant.strip() for variant in (os.environ.get("VARIANTS") or "").split(",") if variant.strip()]

    known = [path for path, _ in VARIANTS]

    declared = gradle_variants()
    if declared is None:
        print("Warning: could not read allVariants from build.gradle, skipping the drift check", file=sys.stderr)
    else:
        missing = [path for path in declared if path not in known]
        extra = [path for path in known if path not in declared]
        if missing or extra:
            details = []
            if missing:
                details.append(f"declared in build.gradle but missing from VARIANTS: {', '.join(missing)}")
            if extra:
                details.append(f"listed in VARIANTS but not declared in build.gradle: {', '.join(extra)}")
            sys.exit("Variant list drift between build.gradle and publish-matrix.py:\n  " + "\n  ".join(details))

    unknown = [variant for variant in requested if variant not in known]
    if unknown:
        sys.exit(f"Unknown variant(s): {', '.join(unknown)}\nKnown variants: {', '.join(known)}")

    selected = [
        {
            "path": path,
            "slug": path.replace("/", "-"),
            "loader": path.split("/")[0],
            "display": LOADER_DISPLAY_NAMES[path.split("/")[0]],
            # mc-publish expects newline-separated lists.
            "versions": "\n".join(versions),
        }
        for path, versions in VARIANTS
        if (loaders == "all" or path.split("/")[0] == loaders)
        and (not requested or path in requested)
    ]

    if not selected:
        sys.exit(f"No variants matched loaders={loaders!r} variants={requested or 'all'}")

    json.dump({"include": selected}, sys.stdout, separators=(",", ":"))
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
