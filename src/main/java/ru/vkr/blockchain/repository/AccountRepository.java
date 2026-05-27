package ru.vkr.blockchain.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import ru.vkr.blockchain.domain.model.Account;
import ru.vkr.blockchain.service.LevelDBService;

import java.io.IOException;
import java.util.*;

@Repository
@RequiredArgsConstructor
@Slf4j
public class AccountRepository {

    private final LevelDBService levelDBService;
    private static final String ACCOUNT_PREFIX = "account:";
    private static final String VALIDATORS_LIST_KEY = "validators_list";
    private static final String BOOTSTRAP_VALIDATORS_KEY = "bootstrap_validators";

    /**
     * Сохраняет аккаунт в LevelDB.
     */
    public void save(Account account) throws IOException {
        saveWithoutValidatorIndexUpdate(account);
    }

    public void saveWithoutValidatorIndexUpdate(Account account) throws IOException {
        String key = ACCOUNT_PREFIX + account.getAddress();
        levelDBService.put(key, account.toBytes());
        log.debug("Account saved: {}", account.getAddress());
    }


    public void initializeValidatorsList(List<String> orderedAddresses) throws IOException {
        if (orderedAddresses == null || orderedAddresses.isEmpty()) {
            throw new IllegalArgumentException("Validator list must not be empty");
        }
        persistValidatorsList(orderedAddresses);
        log.info("Validators list initialized: {}", orderedAddresses);
    }

    public void initializeBootstrapValidators(List<String> orderedAddresses) throws IOException {
        if (orderedAddresses == null || orderedAddresses.isEmpty()) {
            throw new IllegalArgumentException("Bootstrap validator list must not be empty");
        }
        levelDBService.put(BOOTSTRAP_VALIDATORS_KEY, String.join(",", orderedAddresses).getBytes());
        log.info("Bootstrap validators recorded: {}", orderedAddresses);
    }

    /**
     * Адреса валидаторов для round-robin (начальный список + добавленные через UPDATE_ACCOUNT_ROLES).
     */
    public List<String> getValidators() {
        return parseAddressList(levelDBService.get(VALIDATORS_LIST_KEY));
    }

    public List<String> getBootstrapValidators() {
        return parseAddressList(levelDBService.get(BOOTSTRAP_VALIDATORS_KEY));
    }

    public boolean isBootstrapValidator(String address) {
        return getBootstrapValidators().contains(address);
    }

    public boolean hasValidatorsList() {
        return levelDBService.get(VALIDATORS_LIST_KEY) != null;
    }

    /**
     * Импорт аккаунтов и списков валидаторов с другой ноды (только если локальный список ещё пуст).
     */
    public void importJoinSnapshot(List<Account> accounts, List<String> validatorAddresses,
                                 List<String> bootstrapAddresses) throws IOException {
        if (hasValidatorsList()) {
            return;
        }
        if (accounts != null) {
            for (Account account : accounts) {
                if (account.getAddress() == null || exists(account.getAddress())) {
                    continue;
                }
                saveWithoutValidatorIndexUpdate(account);
            }
        }
        if (validatorAddresses != null && !validatorAddresses.isEmpty()) {
            persistValidatorsList(validatorAddresses);
            List<String> bootstrap = bootstrapAddresses != null && !bootstrapAddresses.isEmpty()
                    ? bootstrapAddresses
                    : validatorAddresses;
            if (levelDBService.get(BOOTSTRAP_VALIDATORS_KEY) == null) {
                levelDBService.put(BOOTSTRAP_VALIDATORS_KEY, String.join(",", bootstrap).getBytes());
            }
            log.info("Imported validator lists from peer: validators={}, bootstrap={}", validatorAddresses, bootstrap);
        }
    }

    public void addValidator(String address) throws IOException {
        List<String> validators = new ArrayList<>(getValidators());
        if (!validators.contains(address)) {
            validators.add(address);
            persistValidatorsList(validators);
            log.info("Validator added to round-robin list: {}", address);
        }
    }

    public void removeValidator(String address) throws IOException {
        if (isBootstrapValidator(address)) {
            throw new IllegalStateException("Cannot remove bootstrap validator: " + address);
        }
        List<String> validators = new ArrayList<>(getValidators());
        if (!validators.remove(address)) {
            return;
        }
        if (validators.isEmpty()) {
            throw new IllegalStateException("Cannot remove last validator from the network");
        }
        persistValidatorsList(validators);
        log.info("Validator removed from round-robin list: {}", address);
    }

    private void persistValidatorsList(List<String> orderedAddresses) throws IOException {
        levelDBService.put(VALIDATORS_LIST_KEY, String.join(",", orderedAddresses).getBytes());
    }

    private List<String> parseAddressList(byte[] raw) {
        if (raw == null) {
            return List.of();
        }
        return Arrays.stream(new String(raw).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public Optional<Account> findByAddress(String address) {
        try {
            byte[] data = levelDBService.get(ACCOUNT_PREFIX + address);
            if (data == null) {
                return Optional.empty();
            }
            return Optional.of(Account.fromBytes(data));
        } catch (Exception e) {
            log.error("Error loading account: {}", address, e);
            return Optional.empty();
        }
    }

    public boolean exists(String address) {
        return findByAddress(address).isPresent();
    }

    public void update(Account account) throws IOException {
        save(account);
    }

    public List<Account> findAll() {
        return levelDBService.scanPrefix(ACCOUNT_PREFIX).values().stream()
                .map(bytes -> {
                    try {
                        return Account.fromBytes(bytes);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).toList();
    }
}
