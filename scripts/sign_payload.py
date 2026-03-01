#!/usr/bin/env python3
"""
Скрипт для подписи payload приватным ключом (ECDSA SHA-256).
Совместим с проверкой подписи в Java (CryptoService: SHA256withECDSA, Base64).

Использование:
  python sign_payload.py --private-key <base64_or_path> --payload "текст для подписи"
  python sign_payload.py -k key.pem -p "data"
  echo -n "payload" | python sign_payload.py -k key.pem

Ключ: Base64 DER (PKCS#8 для приватного) или путь к PEM-файлу.
"""

import argparse
import base64
import sys
from pathlib import Path

try:
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.backends import default_backend
    from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature
except ImportError:
    print("Установите: pip install cryptography", file=sys.stderr)
    sys.exit(1)


def load_private_key(key_input: str) -> ec.EllipticCurvePrivateKey:
    """Загружает приватный ключ из Base64 DER или из пути к PEM-файлу."""
    key_input = key_input.strip()
    # Путь к файлу
    if key_input.startswith("-----") or "\n" in key_input:
        return serialization.load_pem_private_key(
            key_input.encode() if key_input.startswith("-----") else key_input.encode(),
            password=None,
            backend=default_backend(),
        )
    path = Path(key_input)
    if path.is_file():
        data = path.read_bytes()
        if data.startswith(b"-----"):
            return serialization.load_pem_private_key(data, password=None, backend=default_backend())
        return serialization.load_der_private_key(data, password=None, backend=default_backend())
    # Считаем Base64 DER
    try:
        der = base64.b64decode(key_input)
        return serialization.load_der_private_key(der, password=None, backend=default_backend())
    except Exception as e:
        raise ValueError(f"Не удалось загрузить приватный ключ: {e}") from e


def sign_payload(private_key: ec.EllipticCurvePrivateKey, payload: str) -> str:
    """Подписывает payload (UTF-8) и возвращает подпись в Base64."""
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.asymmetric import utils as asym_utils

    data = payload.encode("utf-8")
    # Java SHA256withECDSA = ECDSA над SHA-256 от данных
    signature = private_key.sign(data, ec.ECDSA(hashes.SHA256()))
    # Bouncy Castle/Java ECDSA подпись — DER или raw (r,s). Стандарт Java обычно DER.
    # cryptography по умолчанию отдаёт DER, что совместимо с Java verify().
    return base64.b64encode(signature).decode("ascii")


def main():
    parser = argparse.ArgumentParser(
        description="Подписать payload приватным ключом (ECDSA SHA-256, вывод Base64)."
    )
    parser.add_argument(
        "-k", "--private-key",
        required=True,
        metavar="KEY",
        help="Приватный ключ: Base64 DER (PKCS#8) или путь к .pem файлу",
    )
    parser.add_argument(
        "-p", "--payload",
        default=None,
        metavar="TEXT",
        help="Строка для подписи (UTF-8). Если не задана — читается из stdin.",
    )
    parser.add_argument(
        "--public-key",
        default=None,
        metavar="KEY",
        help="Публичный ключ (Base64 или путь к .pem) — опционально, для проверки после подписи",
    )
    args = parser.parse_args()

    payload = args.payload
    if payload is None:
        payload = sys.stdin.read()

    try:
        private_key = load_private_key(args.private_key)
    except ValueError as e:
        print(f"Ошибка ключа: {e}", file=sys.stderr)
        sys.exit(1)

    signature_b64 = sign_payload(private_key, payload)
    print(signature_b64)

    if args.public_key:
        try:
            from cryptography.hazmat.primitives import hashes
            from cryptography.hazmat.primitives.asymmetric import utils as asym_utils

            pub_input = args.public_key.strip()
            if Path(pub_input).is_file():
                pub_data = Path(pub_input).read_bytes()
                if pub_data.startswith(b"-----"):
                    public_key = serialization.load_pem_public_key(pub_data, backend=default_backend())
                else:
                    public_key = serialization.load_der_public_key(pub_data, backend=default_backend())
            else:
                public_key = serialization.load_der_public_key(base64.b64decode(pub_input), backend=default_backend())

            public_key.verify(
                base64.b64decode(signature_b64),
                payload.encode("utf-8"),
                ec.ECDSA(hashes.SHA256()),
            )
            print("(проверка подписи публичным ключом: OK)", file=sys.stderr)
        except Exception as e:
            print(f"(проверка подписи: {e})", file=sys.stderr)
            sys.exit(1)


if __name__ == "__main__":
    main()
