package ru.vkr.blockchain.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.WriteBatch;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.domain.model.Block;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LevelDBService {

    private final DB levelDB;

    public void put(String key, byte[] value) {
        levelDB.put(key.getBytes(), value);
        log.debug("Saved key: {}, size: {} bytes", key, value.length);
    }

    public byte[] get(String key) {
        byte[] value = levelDB.get(key.getBytes());
        log.debug("Retrieved key: {}, found: {}", key, value != null);
        return value;
    }

    public void delete(String key) {
        levelDB.delete(key.getBytes());
        log.debug("Deleted key: {}", key);
    }

    public boolean exists(String key) {
        return levelDB.get(key.getBytes()) != null;
    }

    public void batchWrite(Map<String, byte[]> operations) {
        try (WriteBatch batch = levelDB.createWriteBatch()) {
            operations.forEach((key, value) -> {
                if (value != null) {
                    batch.put(key.getBytes(), value);
                } else {
                    batch.delete(key.getBytes());
                }
            });
            levelDB.write(batch);
            log.debug("Batch write completed: {} operations", operations.size());
        } catch (IOException e) {
            throw new RuntimeException("LevelDB batch write failed", e);
        }
    }

    public List<String> getKeysByPrefix(String prefix) {
        List<String> keys = new ArrayList<>();
        try (DBIterator iterator = levelDB.iterator()) {
            byte[] prefixBytes = prefix.getBytes();
            iterator.seek(prefixBytes);

            while (iterator.hasNext()) {
                Map.Entry<byte[], byte[]> entry = iterator.next();
                String key = new String(entry.getKey());
                if (!key.startsWith(prefix)) {
                    break;
                }
                keys.add(key);
            }
        } catch (IOException e) {
            log.error("Error iterating keys with prefix: {}", prefix, e);
        }
        log.debug("Found {} keys with prefix: {}", keys.size(), prefix);
        return keys;
    }

    public Map<String, byte[]> scanPrefix(String prefix) {
        Map<String, byte[]> entries = new java.util.HashMap<>();
        try (DBIterator iterator = levelDB.iterator()) {
            byte[] prefixBytes = prefix.getBytes();
            iterator.seek(prefixBytes);

            while (iterator.hasNext()) {
                Map.Entry<byte[], byte[]> entry = iterator.next();
                String key = new String(entry.getKey());
                if (!key.startsWith(prefix)) {
                    break;
                }
                entries.put(key, entry.getValue());
            }
        } catch (IOException e) {
            log.error("Error iterating entries with prefix: {}", prefix, e);
        }
        return entries;
    }

    public long countByPrefix(String prefix) {
        long count = 0;
        try (DBIterator iterator = levelDB.iterator()) {
            byte[] prefixBytes = prefix.getBytes();
            iterator.seek(prefixBytes);

            while (iterator.hasNext()) {
                Map.Entry<byte[], byte[]> entry = iterator.next();
                String key = new String(entry.getKey());
                if (!key.startsWith(prefix)) {
                    break;
                }
                count++;
            }
        } catch (IOException e) {
            log.error("Error counting entries with prefix: {}", prefix, e);
        }
        log.debug("Count for prefix {}: {}", prefix, count);
        return count;
    }

    public void deleteByPrefix(String prefix) {
        List<String> keys = getKeysByPrefix(prefix);
        WriteBatch batch = levelDB.createWriteBatch();
        try {
            keys.forEach(key -> batch.delete(key.getBytes()));
            levelDB.write(batch);
            log.info("Deleted {} keys with prefix: {}", keys.size(), prefix);
        } finally {
            try {
                batch.close();
            } catch (IOException e) {
                log.error("Error closing batch during deleteByPrefix", e);
            }
        }
    }

    public List<byte[]> findAllByKeysIn(List<String> keys) {
        List<byte[]> blocks = new ArrayList<>(keys.size());

        for (String key : keys) {
            try {
                byte[] data = levelDB.get(key.getBytes());
                if (data != null) blocks.add(data);
            } catch (Exception e) {
                log.error("Failed to deserialize block with key: {}", key, e);
            }
        }

        log.debug("Found {} blocks out of {} keys", blocks.size(), keys.size());
        return blocks;
    }

    @PreDestroy
    public void close() {
        try {
            levelDB.close();
            log.info("LevelDB closed successfully");
        } catch (IOException e) {
            log.error("Error closing LevelDB", e);
        }
    }
}
