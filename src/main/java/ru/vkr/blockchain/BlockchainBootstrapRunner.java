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
        bootstrapGenesisAndValidators();
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

    private void bootstrapGenesisAndValidators() throws IOException {
        if (blockService.findLatest().isPresent()) {
            return;
        }

        List<String> validatorPublicKeys = bootstrapProperties.getValidatorPublicKeys();
        if (validatorPublicKeys == null || validatorPublicKeys.isEmpty()) {
            throw new IllegalStateException(
                    "blockchain.bootstrap.validator-public-keys must be configured for first startup");
        }

        List<String> validatorAddresses = new ArrayList<>();
        for (String publicKeyBase64 : validatorPublicKeys) {
            if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
                continue;
            }
            String trimmedKey = publicKeyBase64.trim();
            String address = cryptoService.generateAddress(trimmedKey);
            validatorAddresses.add(address);

            Account account = accountRepository.findByAddress(address).orElseGet(() -> {
                Account created = new Account(address, trimmedKey, address);
                created.setAccountRoles(new ArrayList<>(List.of(
                        AccountRole.VALIDATOR, AccountRole.USER, AccountRole.AUDITOR)));
                created.setActive(true);
                return created;
            });
            accountRepository.saveWithoutValidatorIndexUpdate(account);
        }

        if (validatorAddresses.isEmpty()) {
            throw new IllegalStateException("No valid validator public keys in bootstrap configuration");
        }

        accountRepository.initializeValidatorsList(validatorAddresses);
        accountRepository.initializeBootstrapValidators(validatorAddresses);

        String genesisValidator = validatorAddresses.getFirst();
        blockCreationService.createGenesisBlock(genesisValidator, List.of());
        log.info("Bootstrap complete: validators={}, genesis validator={}", validatorAddresses, genesisValidator);
    }
}
