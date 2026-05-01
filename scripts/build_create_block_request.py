#!/usr/bin/env python3
"""
Собирает JSON для POST /api/blocks (Create block).
Подписывает hash_to_sign из ответа Prepare block, подставляет timestamp и transaction_ids.

Использование:
  python build_create_block_request.py -k validator.pem --validator-public-key "MFkw..." --hash-to-sign "28196a14..." --timestamp "2026-03-02T01:23:22.6688185" --transaction-ids "id1,id2"
  или передать transaction_ids как JSON-массив в кавычках.
"""

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sign_payload import load_private_key, sign_payload


def main():
    parser = argparse.ArgumentParser(description="Построить JSON для Create block")
    parser.add_argument("-k", "--private-key", required=True, help="Путь к PEM файлу валидатора")
    parser.add_argument("--validator-public-key", required=True, help="Публичный ключ валидатора (Base64)")
    parser.add_argument("--hash-to-sign", required=True, help="hash_to_sign из ответа POST /api/blocks/prepare")
    parser.add_argument("--timestamp", required=True, help="timestamp из ответа prepare (например 2026-03-02T01:23:22.6688185)")
    parser.add_argument("--transaction-ids", default="[]", help="transaction_ids: JSON-массив или через запятую")
    args = parser.parse_args()

    key_path = Path(args.private_key)
    if not key_path.is_file():
        print(f"Файл не найден: {key_path}", file=sys.stderr)
        sys.exit(1)

    # Парсим transaction_ids
    raw = args.transaction_ids.strip()
    if raw.startswith("["):
        transaction_ids = json.loads(raw)
    else:
        transaction_ids = [s.strip() for s in raw.split(",") if s.strip()]

    private_key = load_private_key(key_path.read_text())
    signature_b64 = sign_payload(private_key, args.hash_to_sign.strip())

    body = {
        "validator_public_key": args.validator_public_key.strip(),
        "validator_signature": signature_b64,
        "timestamp": args.timestamp.strip(),
        "transaction_ids": transaction_ids,
    }
    print(json.dumps(body, indent=2))


if __name__ == "__main__":
    main()
