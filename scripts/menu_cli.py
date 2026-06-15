#!/usr/bin/env python3
"""
Интерактивное CLI для тестирования API узла блокчейна.

  pip install -r scripts/requirements.txt
  python scripts/menu_cli.py
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from blockchain_client import ApiError, BlockchainClient
from blockchain_crypto import (
    address_from_private_key,
    address_from_public_key,
    generate_keypair,
    load_private_key,
    private_key_to_pem,
    public_key_to_base64,
)
from cli_config import CONFIG_PATH, load, save, user_pem_path, validator_pem_paths


def pause(msg: str = "Enter — продолжить…") -> None:
    input(msg)


def print_json(data) -> None:
    print(json.dumps(data, indent=2, ensure_ascii=False, default=str))


def prompt(text: str, default: str = "") -> str:
    if default:
        v = input(f"{text} [{default}]: ").strip()
        return v or default
    return input(f"{text}: ").strip()


class MenuApp:
    def __init__(self) -> None:
        self.cfg = load()
        self.client = BlockchainClient(self.cfg["base_url"])
        self._last_file_hash: str | None = None
        self._last_tx_id: str | None = None
        self._last_user_pub: str | None = None
        self._last_user_priv = None

    def reload_client(self) -> None:
        self.client = BlockchainClient(self.cfg["base_url"])

    def save_cfg(self) -> None:
        save(self.cfg)
        self.reload_client()

    def validator_keys(self):
        pems = validator_pem_paths(self.cfg)
        if not pems:
            raise ApiError("Нет validator PEM. Пункт 0 → добавьте пути или generate_keys.py")
        return self.client.pick_validator_key(pems)

    def user_key(self):
        if self._last_user_priv is not None:
            return self._last_user_priv
        up = user_pem_path(self.cfg)
        if up:
            return load_private_key(up)
        raise ApiError(
            "Нет ключа пользователя. Создайте (п. 2.1), загрузите PEM (п. 0.3) "
            "или укажите user_pem в .cli_config.json"
        )

    # --- actions ---

    def act_status(self) -> None:
        print_json(self.client.network_status())

    def act_validators(self) -> None:
        print_json(self.client.network_validators())

    def act_join_snapshot(self) -> None:
        print_json(self.client.network_join_snapshot())

    def act_next_validator(self) -> None:
        print_json(self.client.next_validator())

    def act_list_blocks(self) -> None:
        page = int(prompt("page", "0") or "0")
        data = self.client.list_blocks(page=page, size=10, sortBy="height", sortDir="desc")
        print_json(data)

    def act_block_hash(self) -> None:
        h = prompt("hash блока")
        print_json(self.client.block_by_hash(h))

    def act_pending(self) -> None:
        print_json(self.client.pending_transactions())

    def act_tx_id(self) -> None:
        print_json(self.client.transaction_by_id(prompt("id транзакции")))

    def act_peers(self) -> None:
        print_json(self.client.list_peers())

    def act_gen_keypair(self) -> None:
        priv, pub = generate_keypair()
        out = Path(__file__).parent / "keys"
        out.mkdir(exist_ok=True)
        name = prompt("имя файла", "test_user")
        pem = out / f"{name}_private.pem"
        pem.write_bytes(private_key_to_pem(priv))
        (out / f"{name}_public.b64").write_text(pub, encoding="utf-8")
        addr = address_from_public_key(pub)
        print(f"Адрес: {addr}")
        print(f"PEM:  {pem}")
        use = prompt("Использовать как текущего пользователя? (y/n)", "y")
        if use.lower() == "y":
            self._last_user_priv = priv
            self._last_user_pub = pub
            self.cfg["user_pem"] = str(pem.relative_to(Path(__file__).parent))
            self.save_cfg()

    def act_create_account(self) -> None:
        vk = self.validator_keys()
        if self._last_user_pub:
            pub = self._last_user_pub
        else:
            pub = prompt("public_key base64 нового пользователя")
        self.client.create_account(vk, pub)
        print("CREATE_ACCOUNT → pending")

    def act_update_roles(self) -> None:
        vk = self.validator_keys()
        target = prompt("target_address")
        roles = prompt("роли через запятую", "USER,VALIDATOR,AUDITOR").replace(" ", "").split(",")
        self.client.update_account_roles(vk, target, roles)
        print("UPDATE_ACCOUNT_ROLES → pending")

    def act_deactivate(self) -> None:
        vk = self.validator_keys()
        self.client.deactivate_account(vk, prompt("target_address"))
        print("DEACTIVATE_ACCOUNT → pending")

    def act_store_data(self) -> None:
        uk = self.user_key()
        text = prompt("текст / JSON", '{"message":"hello from cli"}')
        try:
            payload = json.loads(text)
        except json.JSONDecodeError:
            payload = {"message": text}
        self.client.store_data(uk, payload)
        print("STORE_DATA → pending")

    def act_store_file(self) -> None:
        uk = self.user_key()
        path = Path(prompt("путь к файлу"))
        prev = prompt("previous_transaction_id (пусто = первая версия)", "") or None
        h, tx_id = self.client.store_file(uk, path, previous_transaction_id=prev)
        self._last_file_hash = h
        self._last_tx_id = tx_id
        print(f"STORE_FILE → pending, fileHash={h}, txId={tx_id or '—'}")

    def act_download_file(self) -> None:
        h = prompt("fileHash (sha256 hex)", self._last_file_hash or "")
        src = prompt("source_transaction_id (опц.)", getattr(self, "_last_tx_id", None) or "")
        out = Path(prompt("куда сохранить", f"download_{h[:8]}.bin"))
        uk = self.user_key()
        self.client.download_file(uk, h, out, source_transaction_id=src or None)
        print(f"Скачивание записано (off-chain), файл: {out}")

    def act_prepare_block(self) -> None:
        ids_raw = prompt("transaction_ids через запятую (пусто = все pending)", "")
        ids = [x.strip() for x in ids_raw.split(",") if x.strip()] if ids_raw else None
        print_json(self.client.prepare_block(ids))

    def act_mine_block(self) -> None:
        vk = self.validator_keys()
        prep = self.client.mine_pending(vk)
        print(f"Блок создан, height={prep.get('height')}, txs={len(prep.get('transaction_ids') or [])}")

    def act_add_peer(self) -> None:
        vk = self.validator_keys()
        url = prompt("peer_url", "http://host.docker.internal:8081")
        self.client.add_peer(vk, url)
        print("ADD_PEER → pending (применится в блоке)")

    def act_remove_peer(self) -> None:
        vk = self.validator_keys()
        self.client.remove_peer(vk, prompt("peer_url"))
        print("REMOVE_PEER → pending")

    def act_scenario_full(self) -> None:
        """Краткий сценарий: аккаунт → блок → валидатор → блок."""
        vk = self.validator_keys()
        priv, pub = generate_keypair()
        addr = address_from_public_key(pub)
        print(f"1) CREATE_ACCOUNT {addr}")
        self.client.create_account(vk, pub)
        print("2) Mine block")
        vk = self.validator_keys()
        self.client.mine_pending(vk)
        print("3) Promote to VALIDATOR")
        vk = self.validator_keys()
        self.client.update_account_roles(vk, addr, ["USER", "VALIDATOR", "AUDITOR"])
        vk = self.validator_keys()
        self.client.mine_pending(vk)
        print_json(self.client.network_validators())
        print("Готово.")

    def act_settings_url(self) -> None:
        self.cfg["base_url"] = prompt("base_url", self.cfg["base_url"])
        self.save_cfg()
        print(f"URL: {self.cfg['base_url']}")

    def act_settings_validators(self) -> None:
        cur = ",".join(self.cfg.get("validator_pems") or [])
        raw = prompt("validator PEM (через запятую)", cur)
        self.cfg["validator_pems"] = [x.strip() for x in raw.split(",") if x.strip()]
        self.save_cfg()

    def act_settings_user_pem(self) -> None:
        self.cfg["user_pem"] = prompt("user_pem путь", self.cfg.get("user_pem") or "")
        self._last_user_priv = None
        self.save_cfg()

    def run(self) -> None:
        actions = {
            "1": ("Статус сети", self.act_status),
            "2": ("Валидаторы", self.act_validators),
            "3": ("Следующий валидатор", self.act_next_validator),
            "4": ("Список блоков", self.act_list_blocks),
            "5": ("Блок по hash", self.act_block_hash),
            "6": ("Pending транзакции", self.act_pending),
            "7": ("Транзакция по id", self.act_tx_id),
            "8": ("Список пиров", self.act_peers),
            "9": ("Join snapshot", self.act_join_snapshot),
            "10": ("Сгенерировать ключ пользователя", self.act_gen_keypair),
            "11": ("CREATE_ACCOUNT", self.act_create_account),
            "12": ("UPDATE_ACCOUNT_ROLES", self.act_update_roles),
            "13": ("DEACTIVATE_ACCOUNT", self.act_deactivate),
            "14": ("STORE_DATA", self.act_store_data),
            "15": ("STORE_FILE", self.act_store_file),
            "16": ("Скачать файл", self.act_download_file),
            "17": ("Prepare block", self.act_prepare_block),
            "18": ("Создать блок (mine pending)", self.act_mine_block),
            "19": ("ADD_PEER", self.act_add_peer),
            "20": ("REMOVE_PEER", self.act_remove_peer),
            "21": ("Сценарий: аккаунт + валидатор + блоки", self.act_scenario_full),
            "u": ("Настройка: base URL", self.act_settings_url),
            "v": ("Настройка: validator PEMs", self.act_settings_validators),
            "p": ("Настройка: user PEM", self.act_settings_user_pem),
        }

        while True:
            print()
            print("=" * 56)
            print(f"  Blockchain CLI  →  {self.cfg['base_url']}")
            print(f"  Конфиг: {CONFIG_PATH}")
            pems = validator_pem_paths(self.cfg)
            print(f"  Validator PEMs: {len(pems)}  |  User: {user_pem_path(self.cfg) or '—'}")
            print("=" * 56)
            print("  Чтение:  1 статус  2 валидаторы  3 next  4 блоки  5 блок")
            print("           6 pending  7 tx  8 peers  9 join-snapshot")
            print("  Аккаунты: 10 ключ  11 create  12 roles  13 deactivate")
            print("  Данные:  14 STORE_DATA  15 STORE_FILE  16 download")
            print("  Блоки:   17 prepare  18 mine")
            print("  Пиры:    19 add  20 remove")
            print("  21 полный сценарий")
            print("  Настройки: u URL  v validators  p user PEM")
            print("  q — выход")
            print("-" * 56)
            choice = input("Выбор: ").strip().lower()
            if choice in ("q", "quit", "exit", "0"):
                print("Выход.")
                break
            entry = actions.get(choice)
            if not entry:
                print("Неизвестный пункт.")
                continue
            try:
                entry[1]()
            except ApiError as e:
                print(f"API ошибка: {e}")
                if e.body:
                    print(e.body[:500])
            except Exception as e:
                print(f"Ошибка: {e}")
            pause()


def main() -> None:
    MenuApp().run()


if __name__ == "__main__":
    main()
