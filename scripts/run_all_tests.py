#!/usr/bin/env python3
"""Неинтерактивный прогон основных проверок (для CI / быстрой smoke-проверки)."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--validator-pem", action="append", default=[])
    args = parser.parse_args()

    pems = args.validator_pem or [
        str(ROOT / "keys" / "validator1_private.pem"),
        str(ROOT / "keys" / "validator2_private.pem"),
    ]
    pems = [p for p in pems if Path(p).exists()]
    if not pems:
        print("Нет PEM файлов. Запустите: python scripts/generate_keys.py", file=sys.stderr)
        return 1

    cmd = [
        sys.executable,
        str(ROOT / "test_validator_flow.py"),
        "--base-url",
        args.base_url,
    ]
    for p in pems:
        cmd.extend(["--validator-pem", p])

    print("Running:", " ".join(cmd))
    return subprocess.call(cmd)


if __name__ == "__main__":
    raise SystemExit(main())
