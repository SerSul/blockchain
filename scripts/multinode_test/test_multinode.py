#!/usr/bin/env python3
import argparse
import base64
import hashlib
import json
import sys
import time
from datetime import datetime, timezone

import requests
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature, encode_dss_signature


NODE1 = "http://localhost:8080"
NODE2 = "http://localhost:8081"

# Demo key pair aligned with docker-compose admin public key.
PUBLIC_KEY_B64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEmsd6MU7TnyV08omoXeEG4sK7fge9qc3++kQaz+N88B1wadLPdxs0xN4AJr3ZGvyfEJ51AMdqkrYEwwzKGqbr/A=="
PRIVATE_KEY_PEM = """-----BEGIN PRIVATE KEY-----
MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgg5vUI75y5E/FrEuD
xKakRvvfMtSB4phfiFmW9rx53JGhRANCAASax3oxTtOfJXTyiahd4Qbiwrt+B72p
zf76RBrP43zwHXBp0s93GzTE3gAmvdka/J8QnnUAx2qStgTDDMoapuv8
-----END PRIVATE KEY-----
"""


def load_private_key():
    return serialization.load_pem_private_key(PRIVATE_KEY_PEM.encode("utf-8"), password=None)


def sign_text(private_key, text: str) -> str:
    signature_der = private_key.sign(text.encode("utf-8"), ec.ECDSA(hashes.SHA256()))
    r, s = decode_dss_signature(signature_der)
    normalized_der = encode_dss_signature(r, s)
    return base64.b64encode(normalized_der).decode("utf-8")


def api(method: str, base_url: str, path: str, payload=None, timeout=15):
    url = base_url + path
    response = requests.request(method, url, json=payload, timeout=timeout)
    body = {}
    try:
        body = response.json()
    except Exception:
        body = {"raw": response.text}
    return response.status_code, body


def get_tip_height(base_url: str) -> int:
    status, body = api("GET", base_url, "/api/blocks?page=0&size=1&sortBy=height&sortDir=desc")
    if status != 200:
        raise RuntimeError(f"{base_url} blocks query failed: {status} {body}")
    content = body.get("data", {}).get("content", [])
    if not content:
        return -1
    return int(content[0].get("height", -1))


def wait_for_nodes(timeout_sec=120):
    started = time.time()
    while time.time() - started < timeout_sec:
        try:
            h1 = get_tip_height(NODE1)
            h2 = get_tip_height(NODE2)
            print(f"[ok] nodes alive: node1={h1}, node2={h2}")
            return
        except Exception as ex:
            print(f"[wait] nodes are not ready yet: {ex}")
            time.sleep(3)
    raise TimeoutError("Nodes did not become ready in time")


def create_store_transaction(private_key, base_url: str, note: str):
    payload = json.dumps(
        {"message": note, "ts": datetime.now(timezone.utc).isoformat()},
        ensure_ascii=False,
        separators=(",", ":"),
    )
    request = {
        "creator_public_key": PUBLIC_KEY_B64,
        "transaction_type": "STORE_DATA",
        "payload": payload,
        "content_type": "application/json",
        "signature": sign_text(private_key, payload),
    }
    status, body = api("POST", base_url, "/api/transactions/store", request)
    if status not in (200, 201):
        raise RuntimeError(f"store tx failed: {status} {body}")
    print(f"[ok] pending tx added on {base_url}")


def create_block_from_pending(private_key, base_url: str):
    status, body = api("POST", base_url, "/api/blocks/prepare", {"transaction_ids": []})
    if status != 200:
        raise RuntimeError(f"prepare block failed: {status} {body}")
    data = body.get("data", {})
    hash_to_sign = data.get("hash_to_sign")
    timestamp = data.get("timestamp")
    transaction_ids = data.get("transaction_ids", [])
    if not hash_to_sign or not timestamp:
        raise RuntimeError(f"invalid prepare response: {body}")

    request = {
        "validator_public_key": PUBLIC_KEY_B64,
        "validator_signature": sign_text(private_key, hash_to_sign),
        "timestamp": timestamp,
        "transaction_ids": transaction_ids,
    }
    status, body = api("POST", base_url, "/api/blocks", request)
    if status != 200:
        raise RuntimeError(f"create block failed: {status} {body}")
    print(f"[ok] block created on {base_url}, tx_count={len(transaction_ids)}")


def wait_for_sync(source: str, target: str, timeout_sec=60):
    source_h = get_tip_height(source)
    started = time.time()
    while time.time() - started < timeout_sec:
        target_h = get_tip_height(target)
        print(f"[sync] source={source_h}, target={target_h}")
        if target_h >= source_h:
            print("[ok] target node synced")
            return
        time.sleep(3)
    raise TimeoutError("Sync timeout")


def push_broken_block_to_node(target: str):
    status, body = api("GET", NODE1, "/api/blocks?page=0&size=1&sortBy=height&sortDir=desc")
    if status != 200:
        raise RuntimeError(f"failed to read latest block from node1: {status} {body}")
    tip_hash = body.get("data", {}).get("content", [{}])[0].get("hash")
    if not tip_hash:
        raise RuntimeError("node1 has no blocks")

    status, block_body = api("GET", NODE1, f"/api/blocks/{tip_hash}")
    if status != 200:
        raise RuntimeError(f"failed to get full block: {status} {block_body}")
    block = block_body.get("data")
    if not block:
        raise RuntimeError("empty block dto")

    # Intentional corruption to verify node safety against bad peer payload.
    block["hash"] = "deadbeef" + (block.get("hash") or "")
    status, import_body = api("POST", target, "/api/blocks/sync", block)
    if status != 200:
        raise RuntimeError(f"broken block push failed unexpectedly: {status} {import_body}")
    result = import_body.get("data")
    if result != "INVALID":
        raise RuntimeError(f"expected INVALID for broken block, got: {result}")
    print("[ok] broken block rejected with INVALID")


def _get_latest_block_dto(base_url: str):
    status, body = api("GET", base_url, "/api/blocks?page=0&size=1&sortBy=height&sortDir=desc")
    if status != 200:
        raise RuntimeError(f"failed to read latest block from {base_url}: {status} {body}")
    tip = body.get("data", {}).get("content", [])
    if not tip:
        raise RuntimeError(f"{base_url} has no blocks")
    tip_hash = tip[0].get("hash")
    status, block_body = api("GET", base_url, f"/api/blocks/{tip_hash}")
    if status != 200:
        raise RuntimeError(f"failed to load block dto from {base_url}: {status} {block_body}")
    block = block_body.get("data")
    if not block:
        raise RuntimeError("empty block dto")
    return block


def _calc_block_hash(previous_hash: str, merkle_root: str, timestamp: str, validator_address: str, height: int) -> str:
    raw = f"{previous_hash}{merkle_root}{timestamp}{validator_address}{height}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def push_fork_candidate_to_node(target: str):
    # Build a structurally valid block that does not extend target tip.
    template = _get_latest_block_dto(target)
    target_tip = template.get("hash")
    candidate = dict(template)
    candidate["height"] = int(template.get("height", 0)) + 1
    candidate["previous_hash"] = "fork-prev-" + (target_tip or "none")
    candidate["timestamp"] = datetime.now().replace(microsecond=0).isoformat()
    candidate["status"] = "CONFIRMED"
    candidate["transaction_count"] = len(candidate.get("transactions") or [])
    candidate["hash"] = _calc_block_hash(
        previous_hash=candidate.get("previous_hash"),
        merkle_root=candidate.get("merkle_root"),
        timestamp=candidate.get("timestamp"),
        validator_address=candidate.get("validator_address"),
        height=candidate.get("height"),
    )

    status, import_body = api("POST", target, "/api/blocks/sync", candidate)
    if status != 200:
        raise RuntimeError(f"fork candidate push failed: {status} {import_body}")
    result = import_body.get("data")
    if result != "FORK_CANDIDATE":
        raise RuntimeError(f"expected FORK_CANDIDATE, got: {result}")
    print("[ok] fork candidate accepted as FORK_CANDIDATE")


def run_full():
    private_key = load_private_key()
    wait_for_nodes()
    before_h1 = get_tip_height(NODE1)
    before_h2 = get_tip_height(NODE2)
    print(f"[state] before: node1={before_h1}, node2={before_h2}")

    create_store_transaction(private_key, NODE1, "multinode demo tx")
    create_block_from_pending(private_key, NODE1)
    wait_for_sync(NODE1, NODE2)
    push_broken_block_to_node(NODE2)

    after_h1 = get_tip_height(NODE1)
    after_h2 = get_tip_height(NODE2)
    print(f"[state] after: node1={after_h1}, node2={after_h2}")
    print("[done] full scenario completed successfully")


def main():
    parser = argparse.ArgumentParser(
        description="Smoke test for 2-node blockchain docker-compose demo")
    parser.add_argument(
        "--mode",
        choices=["full", "tx", "tx1", "tx2", "block", "sync", "break", "fork"],
        default="full",
        help="full/tx/tx1/tx2/block/sync/break/fork",
    )
    args = parser.parse_args()
    private_key = load_private_key()

    if args.mode == "full":
        run_full()
        return
    if args.mode == "tx":
        wait_for_nodes()
        create_store_transaction(private_key, NODE1, "single tx mode")
        return
    if args.mode == "tx1":
        wait_for_nodes()
        create_store_transaction(private_key, NODE1, "single tx mode node1")
        return
    if args.mode == "tx2":
        wait_for_nodes()
        create_store_transaction(private_key, NODE2, "single tx mode node2")
        return
    if args.mode == "block":
        wait_for_nodes()
        create_block_from_pending(private_key, NODE1)
        return
    if args.mode == "sync":
        wait_for_nodes()
        wait_for_sync(NODE1, NODE2)
        return
    if args.mode == "break":
        wait_for_nodes()
        push_broken_block_to_node(NODE2)
        return
    if args.mode == "fork":
        wait_for_nodes()
        push_fork_candidate_to_node(NODE2)
        return


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"[error] {exc}", file=sys.stderr)
        sys.exit(1)
