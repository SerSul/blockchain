#!/usr/bin/env python3
"""
E2E-тест PoA: bootstrap-валидаторы, CREATE_ACCOUNT, назначение VALIDATOR, создание блоков.

Перед запуском:
  1. pip install -r scripts/requirements.txt
  2. Сгенерировать ключи: python scripts/generate_keys.py --name validator1
  3. Запустить узел с BLOCKCHAIN_BOOTSTRAP_VALIDATOR_PUBLIC_KEYS=<public.b64>
  4. Указать приватный ключ текущего валидатора (см. GET /api/blocks/next-validator)

Пример:
  python scripts/test_validator_flow.py ^
    --base-url http://localhost:8080 ^
    --validator-pem scripts/keys/validator1_private.pem
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import requests

# запуск из корня репозитория
sys.path.insert(0, str(Path(__file__).resolve().parent))

from blockchain_crypto import (
    address_from_private_key,
    address_from_public_key,
    compact_json,
    generate_keypair,
    load_private_key,
    public_key_to_base64,
    sign_utf8,
)


class FlowError(Exception):
    pass


def api_get(base_url: str, path: str) -> dict:
    r = requests.get(f"{base_url.rstrip('/')}{path}", timeout=30)
    r.raise_for_status()
    body = r.json()
    if not body.get("success", True) and body.get("data") is None and body.get("message"):
        raise FlowError(body["message"])
    return body.get("data")


def api_post(base_url: str, path: str, json_body: dict, expected: int = 200) -> dict | None:
    r = requests.post(f"{base_url.rstrip('/')}{path}", json=json_body, timeout=30)
    if r.status_code != expected:
        raise FlowError(f"POST {path} -> {r.status_code}: {r.text}")
    if r.status_code == 204 or not r.content:
        return None
    body = r.json()
    if not body.get("success", True):
        raise FlowError(body.get("message") or r.text)
    return body.get("data")


def api_patch(base_url: str, path: str, json_body: dict) -> None:
    r = requests.patch(f"{base_url.rstrip('/')}{path}", json=json_body, timeout=30)
    if r.status_code not in (200, 201):
        raise FlowError(f"PATCH {path} -> {r.status_code}: {r.text}")
    body = r.json()
    if not body.get("success", True):
        raise FlowError(body.get("message") or r.text)


def signed_tx(
    private_key,
    transaction_type: str,
    payload_obj: dict,
    content_type: str = "application/json",
) -> dict:
    public_b64 = public_key_to_base64(private_key.public_key())
    payload = compact_json(payload_obj)
    return {
        "creator_public_key": public_b64,
        "transaction_type": transaction_type,
        "payload": payload,
        "content_type": content_type,
        "signature": sign_utf8(payload, private_key),
    }


def get_next_validator_address(base_url: str) -> str | None:
    account = api_get(base_url, "/api/blocks/next-validator")
    return account["address"] if account else None


def pick_signing_key(base_url: str, key_by_address: dict[str, object]):
    next_addr = get_next_validator_address(base_url)
    if not next_addr:
        raise FlowError("No next validator (empty validators list?)")
    if next_addr not in key_by_address:
        raise FlowError(
            f"Current validator is {next_addr}, but no PEM provided for it. "
            f"Available: {list(key_by_address.keys())}"
        )
    print(f"  → signing as validator {next_addr}")
    return key_by_address[next_addr], next_addr


def mine_block(base_url: str, private_key, transaction_ids: list[str] | None = None) -> None:
    prepare_body = {"transaction_ids": transaction_ids or []}
    r = requests.post(
        f"{base_url.rstrip('/')}/api/blocks/prepare",
        json=prepare_body,
        timeout=30,
    )
    r.raise_for_status()
    prep = r.json()["data"]
    hash_to_sign = prep["hash_to_sign"]
    tx_ids = prep.get("transaction_ids") or []

    create_body = {
        "validator_public_key": public_key_to_base64(private_key.public_key()),
        "validator_signature": sign_utf8(hash_to_sign, private_key),
        "timestamp": prep["timestamp"],
        "transaction_ids": tx_ids,
    }
    api_post(base_url, "/api/blocks", create_body)
    print(f"  → block height={prep.get('height')} mined, txs={len(tx_ids)}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Test validator bootstrap and role flow")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument(
        "--validator-pem",
        action="append",
        default=[],
        help="PEM private key of a bootstrap validator (repeat for each)",
    )
    args = parser.parse_args()

    if not args.validator_pem:
        print("Error: pass at least one --validator-pem", file=sys.stderr)
        return 1

    key_by_address = {}
    for pem in args.validator_pem:
        pk = load_private_key(pem)
        key_by_address[address_from_private_key(pk)] = pk

    base = args.base_url
    print("=== 1. Network status ===")
    status = api_get(base, "/api/network/status")
    print(f"  height={status['latest_height']}, validators={status['validator_count']}, "
          f"next={status.get('next_validator_address')}")

    validators_before = api_get(base, "/api/network/validators")
    print(f"  validators in round-robin: {len(validators_before)}")

    print("\n=== 2. Create USER account ===")
    new_private, new_public = generate_keypair()
    new_address = address_from_public_key(new_public)
    print(f"  new user address: {new_address}")

    signer, _ = pick_signing_key(base, key_by_address)
    api_post(
        base,
        "/api/accounts",
        signed_tx(
            signer,
            "CREATE_ACCOUNT",
            {"public_key": new_public},
        ),
        expected=201,
    )
    print("  → CREATE_ACCOUNT in pending pool")

    print("\n=== 3. Mine block (apply CREATE_ACCOUNT) ===")
    signer, _ = pick_signing_key(base, key_by_address)
    mine_block(base, signer)

    print("\n=== 4. Promote user to VALIDATOR (UPDATE_ACCOUNT_ROLES) ===")
    roles_payload = {
        "target_address": new_address,
        "roles": ["USER", "VALIDATOR", "AUDITOR"],
    }
    signer, _ = pick_signing_key(base, key_by_address)
    api_patch(
        base,
        "/api/accounts/roles",
        signed_tx(signer, "UPDATE_ACCOUNT_ROLES", roles_payload),
    )
    print("  → UPDATE_ACCOUNT_ROLES in pending pool")

    print("\n=== 5. Mine block (apply role update) ===")
    signer, _ = pick_signing_key(base, key_by_address)
    mine_block(base, signer)

    validators_after = api_get(base, "/api/network/validators")
    addresses = [v["address"] for v in validators_after]
    if new_address not in addresses:
        raise FlowError(f"New validator {new_address} not in round-robin list: {addresses}")
    print(f"  → validators now: {len(validators_after)} (includes new user)")

    print("\n=== 6. Reject role change on bootstrap validator ===")
    bootstrap = next(v for v in validators_after if v.get("bootstrap"))
    bootstrap_addr = bootstrap["address"]
    signer, _ = pick_signing_key(base, key_by_address)
    bad_tx = signed_tx(
        signer,
        "UPDATE_ACCOUNT_ROLES",
        {"target_address": bootstrap_addr, "roles": ["USER"]},
    )
    r = requests.patch(f"{base.rstrip('/')}/api/accounts/roles", json=bad_tx, timeout=30)
    if r.status_code < 400:
        raise FlowError("Expected failure when changing bootstrap validator roles")
    print(f"  → correctly rejected ({r.status_code})")

    print("\n=== 7. Explorer UI ===")
    print(f"  Blocks:     {base}/explorer/blocks")
    print(f"  Validators: {base}/explorer/validators")
    print(f"  Accounts:   {base}/explorer/accounts")

    print("\nAll steps passed.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except FlowError as e:
        print(f"\nFAILED: {e}", file=sys.stderr)
        raise SystemExit(1)
    except requests.RequestException as e:
        print(f"\nHTTP error: {e}", file=sys.stderr)
        raise SystemExit(1)
