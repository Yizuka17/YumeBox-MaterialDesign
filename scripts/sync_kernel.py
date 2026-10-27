#!/usr/bin/env python3
"""Synchronize the selected mihomo branch and update kernel.properties."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import shutil
import stat
import subprocess


REPO_URL = "https://github.com/MetaCubeX/mihomo.git"


def run(command: list[str], cwd: Path | None = None) -> None:
    subprocess.run(command, cwd=cwd, check=True)


def remove_readonly(func, path: str, _exc_info) -> None:
    """Clear Windows' read-only bit and retry the failed removal."""
    os.chmod(path, stat.S_IWRITE)
    func(path)


def update_kernel_properties(path: Path, branch: str, suffix: str = "", revision: str = "") -> None:
    lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
    values = {
        "external.mihomo.repo": REPO_URL,
        "external.mihomo.branch": branch,
        "external.mihomo.commit": revision,
        "external.mihomo.suffix": suffix,
    }
    seen: set[str] = set()
    updated: list[str] = []
    for line in lines:
        key = line.split("=", 1)[0] if "=" in line else ""
        if key in values:
            updated.append(f"{key}={values[key]}")
            seen.add(key)
        else:
            updated.append(line)
    updated.extend(f"{key}={values[key]}" for key in values if key not in seen)
    temporary = path.with_name(f"{path.name}.tmp.{os.getpid()}")
    temporary.write_text("\n".join(updated) + "\n", encoding="utf-8")
    temporary.replace(path)
    print(f"Updated kernel.properties -> repo={REPO_URL} branch={branch} suffix={suffix}")


def sync_repo(project_root: Path, branch: str, revision: str = "") -> str:
    mihomo_dir = project_root / "lib/mihomo/mihomo"
    if mihomo_dir.exists():
        status = subprocess.check_output(["git", "status", "--porcelain"], cwd=mihomo_dir, text=True)
        if status.strip():
            raise RuntimeError("Mihomo has local changes; preserve them before syncing the kernel")
        run(["git", "fetch", "origin", branch], cwd=mihomo_dir)
    else:
        print(f"Cloning {REPO_URL} (branch {branch}) -> {mihomo_dir}")
        mihomo_dir.parent.mkdir(parents=True, exist_ok=True)
        run(["git", "clone", "--branch", branch, "--single-branch", REPO_URL, str(mihomo_dir)])
    run(["git", "checkout", "--detach", revision or f"origin/{branch}"], cwd=mihomo_dir)
    return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=mihomo_dir, text=True).strip()


def run_tidy(path: Path) -> None:
    if not (path / "go.mod").is_file():
        print(f"Skipping tidy for {path} (no go.mod found)")
        return
    print(f"Running go mod tidy in {path}")
    run(["go", "mod", "tidy"], cwd=path)


def main() -> int:
    parser = argparse.ArgumentParser(description="Synchronize the mihomo kernel source")
    parser.add_argument("channel", choices=("alpha", "Alpha", "meta", "Meta"))
    parser.add_argument("--revision", default=None, help="Exact core commit; defaults to the matching configured channel pin")
    args = parser.parse_args()
    project_root = Path(__file__).resolve().parents[1]
    branch = "Alpha" if args.channel.lower() == "alpha" else "Meta"
    properties = dict(line.split("=", 1) for line in (project_root / "kernel.properties").read_text().splitlines() if "=" in line and not line.startswith("#"))
    revision = args.revision if args.revision is not None else (properties.get("external.mihomo.commit", "") if properties.get("external.mihomo.branch") == branch else "")
    resolved_revision = sync_repo(project_root, branch, revision)
    update_kernel_properties(project_root / "kernel.properties", branch, revision=resolved_revision)
    run_tidy(project_root / "lib/mihomo/mihomo")
    run_tidy(project_root / "lib/native/go")
    print(f"Done: selected {args.channel}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
