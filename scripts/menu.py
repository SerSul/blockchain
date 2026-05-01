#!/usr/bin/env python3
"""
Интерактивное консольное меню для утилит подписи (из папки scripts/).

Запуск из корня репозитория:
  python scripts/menu.py
или из scripts:
  python menu.py
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPTS_DIR.parent

sys.path.insert(0, str(SCRIPTS_DIR))

try:
    from sign_payload import load_private_key, sign_payload
except ImportError:
    print("Установите: pip install -r scripts/requirements.txt", file=sys.stderr)
    sys.exit(1)


def _prompt(label: str, default: str | None = None) -> str:
    hint = f" [{default}]" if default else ""
    raw = input(f"{label}{hint}: ").strip()
    return raw if raw else (default or "")


def _default_pem() -> str:
    for rel in (
        SCRIPTS_DIR / "admin_private.pem",
        REPO_ROOT / "test" / "fixtures" / "demo_key_private.pem",
    ):
        if rel.is_file():
            return str(rel)
    return str(SCRIPTS_DIR / "admin_private.pem")


def action_sign_payload() -> None:
    print("\n--- Подпись строки (как sign_payload.py) ---")
    key_path = _prompt("Путь к приватному PEM", _default_pem())
    p = Path(key_path)
    if not p.is_file():
        print(f"Файл не найден: {p}", file=sys.stderr)
        return
    payload = _prompt("Строка для подписи (UTF-8)")
    if not payload:
        print("Пустой payload.", file=sys.stderr)
        return
    pk = load_private_key(p.read_text(encoding="utf-8"))
    sig = sign_payload(pk, payload)
    print("\nПодпись (Base64):")
    print(sig)


def action_create_account() -> None:
    print("\n--- JSON для POST /api/accounts (CREATE_ACCOUNT) ---")
    key_path = _prompt("Путь к приватному PEM создателя", _default_pem())
    p = Path(key_path)
    if not p.is_file():
        print(f"Файл не найден: {p}", file=sys.stderr)
        return
    public_key = _prompt("Публичный ключ нового пользователя (Base64)")
    if not public_key:
        print("Нужен public key.", file=sys.stderr)
        return
    creator = _prompt("Публичный ключ создателя (пусто = как у нового, bootstrap)", "")
    creator_pk = creator.strip() if creator.strip() else public_key

    payload_str = json.dumps({"public_key": public_key.strip()}, separators=(",", ":"))
    private_key = load_private_key(p.read_text(encoding="utf-8"))
    signature_b64 = sign_payload(private_key, payload_str)
    body = {
        "payload": payload_str,
        "creator_public_key": creator_pk.strip(),
        "signature": signature_b64,
        "transaction_type": "CREATE_ACCOUNT",
    }
    print("\nТело запроса:")
    print(json.dumps(body, indent=2, ensure_ascii=False))


def action_create_block() -> None:
    print("\n--- JSON для POST /api/blocks (после prepare) ---")
    key_path = _prompt("Путь к приватному PEM валидатора", _default_pem())
    p = Path(key_path)
    if not p.is_file():
        print(f"Файл не найден: {p}", file=sys.stderr)
        return
    validator_pub = _prompt("Публичный ключ валидатора (Base64)")
    hash_to_sign = _prompt("hash_to_sign из ответа prepare")
    timestamp = _prompt("timestamp из ответа prepare")
    raw_ids = _prompt("transaction_ids: через запятую или JSON-массив []", "[]")
    raw_ids = raw_ids.strip()
    if raw_ids.startswith("["):
        transaction_ids = json.loads(raw_ids)
    else:
        transaction_ids = [s.strip() for s in raw_ids.split(",") if s.strip()]

    private_key = load_private_key(p.read_text(encoding="utf-8"))
    signature_b64 = sign_payload(private_key, hash_to_sign.strip())
    body = {
        "validator_public_key": validator_pub.strip(),
        "validator_signature": signature_b64,
        "timestamp": timestamp.strip(),
        "transaction_ids": transaction_ids,
    }
    print("\nТело запроса:")
    print(json.dumps(body, indent=2, ensure_ascii=False))


def main() -> None:
    actions = {
        "1": ("Подписать строку (аналог sign_payload.py)", action_sign_payload),
        "2": ("Собрать JSON для CREATE_ACCOUNT", action_create_account),
        "3": ("Собрать JSON для Create block", action_create_block),
    }

    while True:
        print("\n========== Blockchain: утилиты ==========")
        for k, (title, _) in actions.items():
            print(f"  {k}) {title}")
        print("  0) Выход")
        choice = input("\nВыбор: ").strip()

        if choice == "0":
            print("Пока.")
            break
        if choice in actions:
            try:
                actions[choice][1]()
            except (ValueError, json.JSONDecodeError) as e:
                print(f"Ошибка: {e}", file=sys.stderr)
            except KeyboardInterrupt:
                print("\nПрервано.")
        else:
            print("Неизвестный пункт, введите 0–3.")


if __name__ == "__main__":
    main()
