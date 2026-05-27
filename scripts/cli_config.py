#!/usr/bin/env python3
"""Локальная конфигурация CLI (JSON рядом со скриптами)."""

from __future__ import annotations

import json
from pathlib import Path

CONFIG_PATH = Path(__file__).resolve().parent / ".cli_config.json"

DEFAULT = {
    "base_url": "http://localhost:8080",
    "validator_pems": ["keys/validator1_private.pem", "keys/validator2_private.pem"],
    "user_pem": "",
}


def _resolve_pems(pems: list[str]) -> list[str]:
    base = Path(__file__).resolve().parent
    out = []
    for p in pems:
        path = Path(p)
        if not path.is_absolute():
            path = base / p
        if path.exists():
            out.append(str(path))
    return out


def load() -> dict:
    if not CONFIG_PATH.exists():
        cfg = dict(DEFAULT)
        save(cfg)
        return cfg
    cfg = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    for k, v in DEFAULT.items():
        cfg.setdefault(k, v)
    return cfg


def save(cfg: dict) -> None:
    CONFIG_PATH.write_text(json.dumps(cfg, indent=2, ensure_ascii=False), encoding="utf-8")


def validator_pem_paths(cfg: dict | None = None) -> list[Path]:
    cfg = cfg or load()
    return [Path(p) for p in _resolve_pems(cfg.get("validator_pems") or [])]


def user_pem_path(cfg: dict | None = None) -> Path | None:
    cfg = cfg or load()
    raw = (cfg.get("user_pem") or "").strip()
    if not raw:
        return None
    p = Path(raw)
    if not p.is_absolute():
        p = Path(__file__).resolve().parent / p
    return p if p.exists() else None
