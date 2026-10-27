#!/usr/bin/env python3
"""Compute the publish APK file stem used by CI."""

from __future__ import annotations

import argparse


def main() -> int:
    parser = argparse.ArgumentParser(description="Compute a YumeBox publish APK file stem")
    parser.add_argument("prefix")
    parser.add_argument("geo", choices=("builtin", "external"))
    parser.add_argument("abi", choices=("arm64-v8a",))
    parser.add_argument("channel_segment")
    parser.add_argument("tail")
    args = parser.parse_args()
    if not args.tail:
        parser.error("TAIL must not be empty")
    stem = f"{args.prefix}-{args.geo}"
    if args.channel_segment:
        stem += f"-{args.channel_segment}"
    print(f"{stem}-{args.tail}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

