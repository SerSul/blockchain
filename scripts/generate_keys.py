#!/usr/bin/env python3
"""
Генерация пары ключей валидатора для bootstrap и тестов.

Пример:
  python scripts/generate_keys.py --out scripts/keys --name validator1
  python scripts/generate_keys.py --out scripts/keys --name validator2

Публичный ключ (Base64) подставьте в .env:
  BLOCKCHAIN_BOOTSTRAP_VALIDATOR_PUBLIC_KEYS_0=...
  BLOCKCHAIN_BOOTSTRAP_VALIDATOR_PUBLIC_KEYS_1=...
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from blockchain_crypto import (
    address_from_public_key,
    generate_keypair,
    private_key_to_pem,
    public_key_to_base64,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate EC keypair for blockchain node")
    parser.add_argument("--out", default="scripts/keys", help="Output directory")
    parser.add_argument("--name", default="validator1", help="File name prefix")
    args = parser.parse_args()

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    private_key, public_b64 = generate_keypair()
    address = address_from_public_key(public_b64)

    pem_path = out_dir / f"{args.name}_private.pem"
    pub_path = out_dir / f"{args.name}_public.b64"
    addr_path = out_dir / f"{args.name}_address.txt"

    pem_path.write_bytes(private_key_to_pem(private_key))
    pub_path.write_text(public_b64, encoding="utf-8")
    addr_path.write_text(address, encoding="utf-8")

    print(f"Private key:  {pem_path}")
    print(f"Public b64:   {pub_path}")
    print(f"Address:      {address}")
    print()
    print("Bootstrap env (copy to .env):")
    print(public_b64)


if __name__ == "__main__":
    main()
