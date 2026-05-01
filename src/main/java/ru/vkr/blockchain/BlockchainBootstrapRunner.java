package ru.vkr.blockchain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.vkr.blockchain.config.BlockchainBootstrapProperties;
import ru.vkr.blockchain.domain.model.Account;
import ru.vkr.blockchain.domain.model.enums.AccountRole;
import ru.vkr.blockchain.repository.AccountRepository;
import ru.vkr.blockchain.repository.PeerRepository;
import ru.vkr.blockchain.service.BlockCreationService;
import ru.vkr.blockchain.service.CryptoService;
import ru.vkr.blockchain.service.domain.BlockService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(50)
@RequiredArgsConstructor
@Slf4j
public class BlockchainBootstrapRunner implements CommandLineRunner {

    private final BlockchainBootstrapProperties bootstrapProperties;
    private final PeerRepository peerRepository;
    private final AccountRepository accountRepository;
    private final BlockService blockService;
    private final BlockCreationService blockCreationService;
    private final CryptoService cryptoService;

    @Override
    public void run(String... args) throws Exception {
        bootstrapPeers();
        bootstrapGenesisAndAdmin();
    }

    private void bootstrapPeers() {
        List<String> configPeers = bootstrapProperties.getPeers();
        if (configPeers == null || configPeers.isEmpty() || !peerRepository.findAll().isEmpty()) {
            return;
        }
        for (String url : configPeers) {
            if (url != null && !url.isBlank()) {
                peerRepository.add(url.trim());
            }
        }
        log.info("Bootstrap peers loaded: {}", peerRepository.findAll().size());
    }

    private void bootstrapGenesisAndAdmin() throws IOException {
        if (blockService.findLatest().isPresent()) {
            return;
        }

        String adminPublicKey = bootstrapProperties.getAdminPublicKey();
        if (adminPublicKey == null || adminPublicKey.isBlank()) {
            throw new IllegalStateException("blockchain.bootstrap.admin-public-key must be configured for first startup");
        }

        String adminAddress = cryptoService.generateAddress(adminPublicKey);
        Account admin = accountRepository.findByAddress(adminAddress).orElseGet(() -> {
            Account account = new Account(adminAddress, adminPublicKey, adminAddress);
            account.setAccountRoles(new ArrayList<>(List.of(
                    AccountRole.ADMIN, AccountRole.USER, AccountRole.AUDITOR, AccountRole.VALIDATOR)));
            account.setActive(true);
            return account;
        });
        accountRepository.save(admin);

        blockCreationService.createGenesisBlock(adminAddress, List.of());
        log.info("Bootstrap complete: admin={}, genesis created", adminAddress);
    }
}
