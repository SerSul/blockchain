#!/usr/bin/env python3
"""
Собирает JSON для POST /api/peers (ADD_PEER) и POST /api/peers/remove (REMOVE_PEER).
Подписывает payload приватным ключом админа, выводит тело запроса для Postman.

Использование:
  python build_peer_request.py -k admin.pem --creator-public-key "MFkw..." --peer-url "http://localhost:8081" --action add
  python build_peer_request.py -k admin.pem --creator-public-key "MFkw..." --peer-url "http://localhost:8081" --action remove
"""

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sign_payload import load_private_key, sign_payload


def main():
    parser = argparse.ArgumentParser(description="Построить JSON для ADD_PEER / REMOVE_PEER")
    parser.add_argument("-k", "--private-key", required=True, help="Путь к PEM файлу с приватным ключом админа")
    parser.add_argument("--creator-public-key", required=True, help="Публичный ключ админа (Base64)")
    parser.add_argument("--peer-url", required=True, help="URL пира, например http://localhost:8081")
    parser.add_argument("--action", choices=["add", "remove"], required=True, help="add или remove")
    args = parser.parse_args()

    key_path = Path(args.private_key)
    if not key_path.is_file():
        print(f"Файл не найден: {key_path}", file=sys.stderr)
        sys.exit(1)

    payload_obj = {"peer_url": args.peer_url.strip()}
    payload_str = json.dumps(payload_obj, separators=(",", ":"))  # без пробелов, как отдаёт клиент

    private_key = load_private_key(key_path.read_text())
    signature_b64 = sign_payload(private_key, payload_str)

    tx_type = "ADD_PEER" if args.action == "add" else "REMOVE_PEER"
    body = {
        "payload": payload_str,
        "creator_public_key": args.creator_public_key.strip(),
        "signature": signature_b64,
        "transaction_type": tx_type,
    }
    print(json.dumps(body, indent=2))


if __name__ == "__main__":
    main()
