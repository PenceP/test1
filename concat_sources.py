#!/usr/bin/env python3
"""
Concatenate all .kt, .xml, and .kts files into a single mega file for easy copy/paste.
Files are traversed recursively from the repo root, sorted, and separated by markers.
Output: mega_sources.txt
"""

from __future__ import annotations

import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "mega_sources.txt"
SKIP_DIRS = {".git", ".gradle", "build", "out", ".idea"}
EXTS = {".kt", ".xml", ".kts"}


def should_skip_dir(path: Path) -> bool:
    return any(part in SKIP_DIRS for part in path.parts)


def gather_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for dirpath, dirnames, filenames in os.walk(root):
        path = Path(dirpath)
        if should_skip_dir(path):
            dirnames[:] = []  # prune
            continue
        for name in filenames:
            p = path / name
            if p.suffix in EXTS:
                files.append(p)
    return sorted(files)


def main() -> None:
    files = gather_files(ROOT)
    lines = []
    for p in files:
        rel = p.relative_to(ROOT)
        lines.append(f"--- {rel} ---")
        try:
            lines.append(p.read_text(encoding="utf-8"))
        except UnicodeDecodeError:
            # Fallback to latin-1 if encoding is unexpected
            lines.append(p.read_text(encoding="latin-1"))
    lines.append("--- END OF FILE ---")
    OUTPUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {len(files)} files into {OUTPUT}")


if __name__ == "__main__":
    main()
