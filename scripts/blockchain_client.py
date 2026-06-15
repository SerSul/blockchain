#!/usr/bin/env python3
"""HTTP-клиент REST API узла блокчейна."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import requests

from blockchain_crypto import (
    address_from_private_key,
    address_from_public_key,
    compact_json,
    public_key_to_base64,
    sign_utf8,
)


class ApiError(Exception):
    def __init__(self, message: str, status: int | None = None, body: str | None = None):
        super().__init__(message)
        self.status = status
        self.body = body


class BlockchainClient:
    def __init__(self, base_url: str = "http://localhost:8080", timeout: int = 60):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def _url(self, path: str) -> str:
        return f"{self.base_url}{path}"

    def _parse(self, r: requests.Response) -> Any:
        if r.status_code == 404:
            return None
        try:
            body = r.json()
        except json.JSONDecodeError:
            if r.ok:
                return r.text
            raise ApiError(r.text or r.reason, r.status_code, r.text)
        if not r.ok:
            msg = body.get("message") if isinstance(body, dict) else r.text
            raise ApiError(msg or r.text, r.status_code, r.text)
        if isinstance(body, dict) and "data" in body:
            return body["data"]
        return body

    def get_raw(self, path: str, **params) -> requests.Response:
        return requests.get(self._url(path), params=params or None, timeout=self.timeout)

    def get(self, path: str, **params) -> Any:
        return self._parse(self.get_raw(path, **params))

    def post(self, path: str, json_body: dict | None = None, expected: set[int] | None = None) -> Any:
        r = requests.post(self._url(path), json=json_body, timeout=self.timeout)
        ok = {200, 201} if expected is None else expected
        if r.status_code not in ok:
            raise ApiError(r.text, r.status_code, r.text)
        return self._parse(r)

    def patch(self, path: str, json_body: dict) -> Any:
        r = requests.patch(self._url(path), json=json_body, timeout=self.timeout)
        if r.status_code not in (200, 201):
            raise ApiError(r.text, r.status_code, r.text)
        return self._parse(r)

    # --- network ---
    def network_status(self) -> dict:
        return self.get("/api/network/status")

    def network_validators(self) -> list:
        return self.get("/api/network/validators") or []

    def network_join_snapshot(self) -> dict:
        return self.get("/api/network/join-snapshot")

    # --- blocks ---
    def next_validator(self) -> dict | None:
        return self.get("/api/blocks/next-validator")

    def list_blocks(self, page: int = 0, size: int = 10, **filters) -> dict:
        return self.get("/api/blocks", page=page, size=size, **filters)

    def block_by_hash(self, block_hash: str) -> dict | None:
        return self.get(f"/api/blocks/{block_hash}")

    def blocks_range(self, from_height: int, limit: int = 50) -> list:
        return self.get("/api/blocks/range", fromHeight=from_height, limit=limit) or []

    def prepare_block(self, transaction_ids: list[str] | None = None) -> dict:
        body = {"transaction_ids": transaction_ids or []}
        return self.post("/api/blocks/prepare", body)

    def create_block(
        self,
        private_key,
        hash_to_sign: str,
        timestamp,
        transaction_ids: list[str] | None = None,
    ) -> None:
        body = {
            "validator_public_key": public_key_to_base64(private_key.public_key()),
            "validator_signature": sign_utf8(hash_to_sign, private_key),
            "timestamp": timestamp,
            "transaction_ids": transaction_ids or [],
        }
        self.post("/api/blocks", body)

    def mine_pending(self, private_key) -> dict:
        prep = self.prepare_block()
        self.create_block(
            private_key,
            prep["hash_to_sign"],
            prep["timestamp"],
            prep.get("transaction_ids"),
        )
        return prep

    # --- transactions ---
    def pending_transactions(self, page: int = 0, size: int = 50) -> dict:
        return self.get("/api/transactions/pending", page=page, size=size)

    def transaction_by_id(self, tx_id: str) -> dict | None:
        return self.get(f"/api/transactions/{tx_id}")

    def list_transactions(self, page: int = 0, size: int = 20, **filters) -> dict:
        return self.get("/api/transactions", page=page, size=size, **filters)

    # --- peers ---
    def list_peers(self) -> list:
        return self.get("/api/peers") or []

    # --- signed transactions ---
    @staticmethod
    def build_signed_tx(
        private_key,
        transaction_type: str,
        payload_obj: dict,
        content_type: str = "application/json",
    ) -> dict:
        payload = compact_json(payload_obj)
        return {
            "creator_public_key": public_key_to_base64(private_key.public_key()),
            "transaction_type": transaction_type,
            "payload": payload,
            "content_type": content_type,
            "signature": sign_utf8(payload, private_key),
        }

    def create_account(self, validator_key, new_user_public_b64: str) -> None:
        tx = self.build_signed_tx(
            validator_key,
            "CREATE_ACCOUNT",
            {"public_key": new_user_public_b64},
        )
        self.post("/api/accounts", tx, expected={201})

    def update_account_roles(self, validator_key, target_address: str, roles: list[str]) -> None:
        tx = self.build_signed_tx(
            validator_key,
            "UPDATE_ACCOUNT_ROLES",
            {"target_address": target_address, "roles": roles},
        )
        self.patch("/api/accounts/roles", tx)

    def deactivate_account(self, validator_key, target_address: str) -> None:
        tx = self.build_signed_tx(
            validator_key,
            "DEACTIVATE_ACCOUNT",
            {"target_address": target_address},
        )
        self.post("/api/accounts/deactivate", tx)

    def store_data(self, user_key, payload_obj: dict) -> None:
        tx = self.build_signed_tx(user_key, "STORE_DATA", payload_obj)
        self.post("/api/transactions/store", tx)

    def store_file(
        self,
        user_key,
        file_path: Path,
        file_name: str | None = None,
        previous_transaction_id: str | None = None,
    ) -> tuple[str, str | None]:
        import hashlib

        path = Path(file_path)
        data = path.read_bytes()
        file_hash = hashlib.sha256(data).hexdigest()
        name = file_name or path.name
        payload_obj: dict = {"fileName": name, "fileHash": file_hash, "size": len(data)}
        if previous_transaction_id:
            payload_obj["previous_transaction_id"] = previous_transaction_id
        payload = compact_json(payload_obj)
        signature = sign_utf8(payload, user_key)
        public_b64 = public_key_to_base64(user_key.public_key())
        with path.open("rb") as f:
            r = requests.post(
                self._url("/api/transactions/store-file"),
                files={"file": (name, f)},
                data={
                    "creator_public_key": public_b64,
                    "payload": payload,
                    "signature": signature,
                    "content_type": "application/octet-stream",
                },
                timeout=self.timeout,
            )
        if r.status_code != 200:
            raise ApiError(r.text, r.status_code, r.text)
        body = r.json()
        data = body.get("data") or {}
        tx_id = data.get("transaction_id") if isinstance(data, dict) else None
        return file_hash, tx_id

    def record_download(self, user_key, file_hash: str, source_transaction_id: str | None = None) -> None:
        payload_obj: dict = {"file_hash": file_hash}
        if source_transaction_id:
            payload_obj["source_transaction_id"] = source_transaction_id
        payload = compact_json(payload_obj)
        body = {
            "creator_public_key": public_key_to_base64(user_key.public_key()),
            "file_hash": file_hash,
            "signature": sign_utf8(payload, user_key),
        }
        if source_transaction_id:
            body["source_transaction_id"] = source_transaction_id
        self.post("/api/trace/downloads", body)

    def download_file(self, user_key, file_hash: str, out_path: Path, source_transaction_id: str | None = None) -> Path:
        self.record_download(user_key, file_hash, source_transaction_id)
        r = self.get_raw(f"/api/transactions/files/{file_hash}")
        if r.status_code != 200:
            raise ApiError(r.text, r.status_code, r.text)
        out_path.write_bytes(r.content)
        return out_path

    def add_peer(self, validator_key, peer_url: str) -> None:
        tx = self.build_signed_tx(validator_key, "ADD_PEER", {"peer_url": peer_url})
        self.post("/api/peers", tx, expected={201})

    def remove_peer(self, validator_key, peer_url: str) -> None:
        tx = self.build_signed_tx(validator_key, "REMOVE_PEER", {"peer_url": peer_url})
        self.post("/api/peers/remove", tx)

    def pick_validator_key(self, validator_pems: list[Path]) -> Any:
        """Выбирает PEM текущего валидатора по /api/blocks/next-validator."""
        from blockchain_crypto import load_private_key

        next_v = self.next_validator()
        if not next_v:
            raise ApiError("No next validator (empty network?)")
        expected = next_v.get("address")
        for pem in validator_pems:
            pk = load_private_key(pem)
            if address_from_private_key(pk) == expected:
                return pk
        raise ApiError(
            f"Current validator is {expected}, no matching PEM in {validator_pems}"
        )
