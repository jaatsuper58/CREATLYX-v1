#!/usr/bin/env python3
"""Regenerates android/app/baseline-prof.txt from codebase symbols.

Emits ART profile rules for the cold-start hot path: application/activity
lifecycle plus every top-level @Composable in app + feature modules (the
Compose runtime and androidx libraries ship their own profiles inside their
AARs, so only app code needs curation here). Preview composables are
skipped. Run after adding screens:

    python3 tools/perf/gen_baseline.py
"""
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2] / "android"
OUT = ROOT / "app" / "baseline-prof.txt"

HEADER = """# ChattlyX baseline profile (curated; cold-start hot path).
# Regenerate with tools/perf/gen_baseline.py after adding screens, and refine
# on a reference device via :benchmark's BaselineProfileGenerator
# (Section 10 budgets: < 2 s cold start on a 3 GB mid-range phone).
# Compose/androidx libraries ship their own profiles; only app code is listed.
"""

LIFECYCLE = [
    "HSPLcom/chattlyx/app/ChattlyxApplication;->onCreate()V",
    "HSPLcom/chattlyx/app/MainActivity;->onCreate(Landroid/os/Bundle;)V",
    "HSPLandroidx/activity/ComponentActivity;->onCreate(Landroid/os/Bundle;)V",
    "HSPLandroidx/compose/ui/platform/AndroidComposeView;->onCreate()V",
]

COMPOSABLE = re.compile(r"^package\s+(\S+)")
FUN = re.compile(r"\bfun\s+([A-Za-z0-9_]+)\s*\(")


def kt_files():
    out = subprocess.run(
        ["bash", "-c",
         "find app/src/main feature/*/src/main -name '*.kt' "
         "| xargs grep -l '@Composable' 2>/dev/null"],
        cwd=ROOT, capture_output=True, text=True, check=True,
    )
    return [ROOT / line for line in out.stdout.splitlines() if line]


def composables(path):
    pkg = None
    names = []
    annotated = False  # previous non-blank line carried @Composable
    for line in path.read_text(encoding="utf-8").splitlines():
        m = COMPOSABLE.match(line)
        if m:
            pkg = m.group(1)
            annotated = False
            continue
        if not line.strip():
            continue
        stripped = line.strip()
        if stripped.startswith("@"):
            if "@Composable" in stripped:
                annotated = True
            continue  # stacked annotations keep the flag alive
        if annotated:
            fm = FUN.search(stripped)
            if fm:
                names.append(fm.group(1))
        annotated = False
    return pkg, names


def main():
    rules = list(LIFECYCLE)
    for path in sorted(kt_files()):
        pkg, names = composables(path)
        if not pkg or not names:
            continue
        cls = f"{pkg}/{path.stem}Kt".replace(".", "/")
        for name in names:
            if name.endswith("Preview") or name.startswith("Preview"):
                continue
            rules.append(
                f"HSPL{cls};->{name}(Landroidx/compose/runtime/Composer;I)V",
            )
    seen = set()
    unique = [r for r in rules if not (r in seen or seen.add(r))]
    OUT.write_text(HEADER + "\n".join(unique) + "\n", encoding="utf-8")
    print(f"wrote {len(unique)} rules -> {OUT.relative_to(ROOT.parent)}")


if __name__ == "__main__":
    main()
