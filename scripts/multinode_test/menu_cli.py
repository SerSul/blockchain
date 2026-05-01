#!/usr/bin/env python3
import sys

import test_multinode as t


def print_menu():
    print("\n=== Blockchain Multi-node Menu ===")
    print("1) Проверить доступность нод")
    print("2) Показать высоты node1/node2")
    print("3) Добавить STORE_DATA транзакцию в node1")
    print("4) Добавить STORE_DATA транзакцию в node2")
    print("5) Создать блок на node1 из pending")
    print("6) Создать блок на node2 из pending")
    print("7) Дождаться синка node2 <- node1")
    print("8) Отправить сломанный блок в node2 (ожидаем INVALID)")
    print("9) Отправить fork candidate в node2 (ожидаем FORK_CANDIDATE)")
    print("10) Запустить full сценарий")
    print("0) Выход")


def show_heights():
    h1 = t.get_tip_height_safe(t.NODE1)
    h2 = t.get_tip_height_safe(t.NODE2)
    print(f"[state] node1={'down' if h1 is None else h1}, node2={'down' if h2 is None else h2}")


def action_add_tx(private_key, node_url: str):
    note = input("Введите текст payload-note (Enter = default): ").strip()
    if not note:
        note = "manual menu tx"
    t.create_store_transaction(private_key, node_url, note)


def main():
    private_key = t.load_private_key()

    while True:
        print_menu()
        choice = input("Выбери пункт: ").strip()

        try:
            if choice == "1":
                t.wait_for_nodes(require_both=False)
            elif choice == "2":
                show_heights()
            elif choice == "3":
                if not t.wait_for_node(t.NODE1):
                    print("[warn] node1 недоступна, операция пропущена")
                    continue
                action_add_tx(private_key, t.NODE1)
            elif choice == "4":
                if not t.wait_for_node(t.NODE2):
                    print("[warn] node2 недоступна, операция пропущена")
                    continue
                action_add_tx(private_key, t.NODE2)
            elif choice == "5":
                if not t.wait_for_node(t.NODE1):
                    print("[warn] node1 недоступна, операция пропущена")
                    continue
                t.create_block_from_pending(private_key, t.NODE1)
            elif choice == "6":
                if not t.wait_for_node(t.NODE2):
                    print("[warn] node2 недоступна, операция пропущена")
                    continue
                t.create_block_from_pending(private_key, t.NODE2)
            elif choice == "7":
                t.wait_for_nodes(require_both=False)
                t.wait_for_sync(t.NODE1, t.NODE2)
            elif choice == "8":
                t.wait_for_nodes(require_both=False)
                t.push_broken_block_to_node(t.NODE2)
            elif choice == "9":
                t.wait_for_nodes(require_both=False)
                t.push_fork_candidate_to_node(t.NODE2)
            elif choice == "10":
                t.run_full()
            elif choice == "0":
                print("Выход.")
                return
            else:
                print("Неизвестный пункт. Введи число из меню.")
        except KeyboardInterrupt:
            print("\nОперация прервана.")
        except Exception as exc:
            print(f"[error] {exc}")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nВыход.")
        sys.exit(0)
