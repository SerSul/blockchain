package ru.vkr.blockchain.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import ru.vkr.blockchain.service.LevelDBService;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Хранение списка адресов пиров (нод) в LevelDB.
 * Ключ: {@value #PEERS_KEY}, значение: список URL через запятую.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class PeerRepository {

    private static final String PEERS_KEY = "peers";

    private final LevelDBService levelDBService;

    public List<String> findAll() {
        byte[] data = levelDBService.get(PEERS_KEY);
        if (data == null) return List.of();
        String list = new String(data);
        return Arrays.stream(list.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public void add(String peerUrl) {
        String normalized = peerUrl.trim();
        if (normalized.isEmpty()) return;
        Set<String> peers = new java.util.HashSet<>(findAll());
        peers.add(normalized);
        saveAll(peers);
        log.debug("Added peer: {}", normalized);
    }

    public void remove(String peerUrl) {
        String normalized = peerUrl.trim();
        List<String> list = findAll();
        List<String> kept = list.stream()
                .filter(p -> !p.equalsIgnoreCase(normalized))
                .toList();
        if (kept.size() == list.size()) {
            log.debug("Peer not found, nothing to remove: {}", normalized);
            return;
        }
        levelDBService.put(PEERS_KEY, String.join(",", kept).getBytes());
        log.debug("Removed peer: {}", normalized);
    }

    public boolean exists(String peerUrl) {
        return findAll().stream()
                .anyMatch(p -> p.equalsIgnoreCase(peerUrl.trim()));
    }

    private void saveAll(Set<String> peers) {
        String value = String.join(",", peers);
        levelDBService.put(PEERS_KEY, value.getBytes());
    }
}
