#!/usr/bin/env python3
"""
Собирает JSON для POST /api/accounts (CREATE_ACCOUNT).
Payload — JSON-строка {"public_key":"<Base64>"}. Подпись ставится от этой строки.

Использование:
  python build_create_account_request.py -k admin.pem --public-key "MFkw..."
  python build_create_account_request.py -k admin.pem --public-key "MFkw..." --creator-public-key "MFkw..."  # админ создаёт другого
"""

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sign_payload import load_private_key, sign_payload


def main():
    parser = argparse.ArgumentParser(description="Построить JSON для CREATE_ACCOUNT")
    parser.add_argument("-k", "--private-key", required=True, help="Путь к PEM файлу с приватным ключом")
    parser.add_argument("--public-key", required=True, help="Публичный ключ нового пользователя (Base64)")
    parser.add_argument("--creator-public-key", default=None, help="Публичный ключ создателя (по умолчанию = public-key, для bootstrap)")
    parser.add_argument("--transaction-type", default="CREATE_ACCOUNT", help="Тип транзакции")
    args = parser.parse_args()

    key_path = Path(args.private_key)
    if not key_path.is_file():
        print(f"Файл не найден: {key_path}", file=sys.stderr)
        sys.exit(1)

    public_key = args.public_key.strip()
    creator_public_key = (args.creator_public_key or public_key).strip()

    payload_obj = {"public_key": public_key}
    payload_str = json.dumps(payload_obj, separators=(",", ":"))

    private_key = load_private_key(key_path.read_text())
    signature_b64 = sign_payload(private_key, payload_str)

    body = {
        "payload": payload_str,
        "creator_public_key": creator_public_key,
        "signature": signature_b64,
        "transaction_type": args.transaction_type,
    }
    print(json.dumps(body, indent=2))


if __name__ == "__main__":
    main()
