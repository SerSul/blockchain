package ru.vkr.blockchain.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import ru.vkr.blockchain.domain.model.enums.AccountRole;
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

    /**
     * Сохраняет аккаунт в LevelDB
     */
    public void save(Account account) throws IOException {
        Optional<Account> existing = findByAddress(account.getAddress());
        boolean hadValidator = existing.map(a -> a.getAccountRoles().contains(AccountRole.VALIDATOR)).orElse(false);
        boolean hasValidator = account.getAccountRoles().contains(AccountRole.VALIDATOR);

        String key = ACCOUNT_PREFIX + account.getAddress();
        levelDBService.put(key, account.toBytes());

        if (hasValidator) {
            addToValidatorIndex(account.getAddress());
        } else if (hadValidator) {
            removeFromValidatorIndex(account.getAddress());
        }

        log.debug("Account saved: {}", account.getAddress());
    }

    /**
     * Возвращает список адресов валидаторов (отсортирован для детерминированного round-robin)
     */
    public List<String> getValidators() {
        byte[] validatorListBytes = levelDBService.get(VALIDATORS_LIST_KEY);
        if (validatorListBytes == null) return List.of();

        String validatorList = new String(validatorListBytes);
        return Arrays.stream(validatorList.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted()
                .toList();
    }

    /**
     * Находит аккаунт по адресу
     */
    public Optional<Account> findByAddress(String address) {
        try {
            byte[] data = levelDBService.get(ACCOUNT_PREFIX + address);
            if (data == null) return Optional.empty();
            return Optional.of(Account.fromBytes(data));
        } catch (Exception e) {
            log.error("Error loading account: {}", address, e);
            return Optional.empty();
        }
    }

    /**
     * Проверяет, существует ли аккаунт
     */
    public boolean exists(String address) {
        return findByAddress(address).isPresent();
    }

    /**
     * Удаляет аккаунт
     */
    public void delete(String address) throws IOException {
        levelDBService.delete(ACCOUNT_PREFIX + address);
        removeFromValidatorIndex(address);
        log.debug("Account deleted: {}", address);
    }

    /**
     * Обновляет аккаунт (по сути то же самое, что save)
     */
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

    /**
     * Добавляет адрес в индекс валидаторов
     */
    private void addToValidatorIndex(String address) {
        try {
            byte[] validatorListBytes = levelDBService.get(VALIDATORS_LIST_KEY);
            Set<String> validators = new HashSet<>();

            if (validatorListBytes != null) {
                String validatorList = new String(validatorListBytes);
                validators.addAll(Arrays.asList(validatorList.split(",")));
            }

            validators.add(address);
            String updatedList = String.join(",", validators);
            levelDBService.put(VALIDATORS_LIST_KEY, updatedList.getBytes());

            log.debug("Added to validator index: {}", address);
        } catch (Exception e) {
            log.error("Error adding to validator index: {}", address, e);
        }
    }

    /**
     * Удаляет адрес из индекса валидаторов
     */
    private void removeFromValidatorIndex(String address) {
        try {
            byte[] validatorListBytes = levelDBService.get(VALIDATORS_LIST_KEY);
            if (validatorListBytes == null) return;

            String validatorList = new String(validatorListBytes);
            Set<String> validators = new HashSet<>(Arrays.asList(validatorList.split(",")));
            validators.remove(address);

            String updatedList = String.join(",", validators);
            levelDBService.put(VALIDATORS_LIST_KEY, updatedList.getBytes());

            log.debug("Removed from validator index: {}", address);
        } catch (Exception e) {
            log.error("Error removing from validator index: {}", address, e);
        }
    }
}
