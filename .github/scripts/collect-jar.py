#!/usr/bin/env python3
"""Collects the release JAR of one build variant into dist/.

Usage:
    python3 .github/scripts/collect-jar.py <loader> <minecraft>

Reads the variant's mod_version from <loader>/<minecraft>/gradle.properties and
copies the matching JAR into dist/, skipping sources JARs.
"""

import shutil
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]


def read_mod_version(variant_dir):
    """Returns the mod_version declared by the variant, or None when it is absent."""
    properties = variant_dir / "gradle.properties"
    if not properties.is_file():
        return None

    for line in properties.read_text(encoding="utf-8").splitlines():
        if line.startswith("mod_version="):
            return line.split("=", 1)[1].strip()
    return None


def main() -> None:
    """Copies the release JAR produced by the variant into dist/."""
    if len(sys.argv) != 3:
        sys.exit("Usage: collect-jar.py <loader> <minecraft>")

    variant = f"{sys.argv[1]}/{sys.argv[2]}"
    variant_dir = REPO_ROOT / variant

    if not variant_dir.is_dir():
        sys.exit(f"Unknown variant: {variant}")

    mod_version = read_mod_version(variant_dir)
    if mod_version is None:
        sys.exit(f"{variant}/gradle.properties does not declare mod_version")

    libs = variant_dir / "build" / "libs"
    candidates = sorted(
        jar for jar in libs.glob(f"*-{mod_version}.jar")
        if not jar.name.endswith("-sources.jar")
    ) if libs.is_dir() else []

    if len(candidates) != 1:
        found = ", ".join(jar.name for jar in candidates) or "none"
        sys.exit(f"Expected exactly one release JAR for {variant} {mod_version}, found: {found}")

    dist = REPO_ROOT / "dist"
    dist.mkdir(parents=True, exist_ok=True)
    shutil.copy(candidates[0], dist / candidates[0].name)
    print(f"Collected {variant} {mod_version} -> dist/{candidates[0].name}")


if __name__ == "__main__":
    main()
