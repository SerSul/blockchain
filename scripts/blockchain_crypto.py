#!/usr/bin/env python3
"""Криптография узла: EC secp256r1, SHA256withECDSA, адрес 0x + 40 hex от SHA-256(SPKI)."""

from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec


def generate_keypair() -> tuple[ec.EllipticCurvePrivateKey, str]:
    private_key = ec.generate_private_key(ec.SECP256R1())
    public_b64 = public_key_to_base64(private_key.public_key())
    return private_key, public_b64


def load_private_key(pem_path: str | Path) -> ec.EllipticCurvePrivateKey:
    data = Path(pem_path).read_bytes()
    return serialization.load_pem_private_key(data, password=None)


def public_key_to_base64(public_key: ec.EllipticCurvePublicKey) -> str:
    der = public_key.public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    return base64.b64encode(der).decode("ascii")


def private_key_to_pem(private_key: ec.EllipticCurvePrivateKey) -> bytes:
    return private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )


def address_from_public_key(public_key_b64: str) -> str:
    der = base64.b64decode(public_key_b64)
    digest = hashlib.sha256(der).hexdigest()
    return "0x" + digest[:40]


def address_from_private_key(private_key: ec.EllipticCurvePrivateKey) -> str:
    return address_from_public_key(public_key_to_base64(private_key.public_key()))


def sign_utf8(data: str, private_key: ec.EllipticCurvePrivateKey) -> str:
    signature = private_key.sign(data.encode("utf-8"), ec.ECDSA(hashes.SHA256()))
    return base64.b64encode(signature).decode("ascii")


def compact_json(obj: dict) -> str:
    return json.dumps(obj, separators=(",", ":"), ensure_ascii=False)
