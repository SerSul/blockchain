# Multi-node Docker Demo

## What this gives you
- Two blockchain nodes: `node1` (`localhost:8080`) and `node2` (`localhost:8081`)
- Two independent PostgreSQL databases:
  - `postgres-node1` (`localhost:5433`, DB `blockchain_db_node1`)
  - `postgres-node2` (`localhost:5434`, DB `blockchain_db_node2`)
- Separate LevelDB volumes per node
- Python smoke test that can:
  - add transaction to node1/node2
  - create block
  - wait for sync
  - push intentionally broken block payload
  - push fork candidate payload

## Start
Create local env file first:
```powershell
copy .env.example .env
```
Then set your values in `.env` (at minimum `POSTGRES_PASSWORD` and `BLOCKCHAIN_BOOTSTRAP_ADMIN_PUBLIC_KEY`).

Start stack:
```powershell
docker compose up -d --build
```

Check containers:
```powershell
docker compose ps
```

## Run test script
```powershell
python -m pip install -r scripts/multinode_test/requirements.txt
python scripts/multinode_test/test_multinode.py --mode full
```

## Run interactive console UI
```powershell
python scripts/multinode_test/menu_cli.py
```

## Modes
- `--mode full` : all steps end-to-end
- `--mode tx` : only add pending transaction on node1
- `--mode tx1` : add pending transaction on node1
- `--mode tx2` : add pending transaction on node2
- `--mode block` : create block on node1 from pending
- `--mode sync` : wait until node2 catches node1
- `--mode break` : send corrupted block to node2 (expects `INVALID`)
- `--mode fork` : send valid-but-divergent block to node2 (expects `FORK_CANDIDATE`)

## Stop and clean
```powershell
docker compose down
```

Drop all persisted data (Postgres + LevelDB volumes):
```powershell
docker compose down -v
```
