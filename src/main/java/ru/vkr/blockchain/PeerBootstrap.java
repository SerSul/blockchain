package ru.vkr.blockchain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.vkr.blockchain.config.BlockchainBootstrapProperties;
import ru.vkr.blockchain.repository.PeerRepository;

import java.util.List;

/**
 * При старте добавляет пиров из конфига (blockchain.bootstrap.peers), если список пиров пуст.
 */
@Component
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class PeerBootstrap implements CommandLineRunner {

    private final PeerRepository peerRepository;
    private final BlockchainBootstrapProperties bootstrapProperties;

    @Override
    public void run(String... args) {
        List<String> configPeers = bootstrapProperties.getPeers();
        if (configPeers == null || configPeers.isEmpty()) {
            return;
        }
        if (!peerRepository.findAll().isEmpty()) {
            log.debug("Peers already present, skipping bootstrap peers from config");
            return;
        }
        for (String url : configPeers) {
            if (url != null && !url.isBlank()) {
                peerRepository.add(url.trim());
                log.info("Bootstrap peer added: {}", url.trim());
            }
        }
    }
}
