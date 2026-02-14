package ru.vkr.blockchain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.domain.model.Account;
import ru.vkr.blockchain.repository.AccountRepository;
import ru.vkr.blockchain.service.domain.BlockService;

import java.util.List;
import java.util.Optional;

/**
 * Выбор валидатора для следующего блока по round-robin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidatorSelectionService {

    private final BlockChainService blockChainService;
    private final AccountRepository accountRepository;

    /**
     * Возвращает адрес валидатора для следующего блока (round-robin).
     * Формула: validators.get(nextHeight % validators.size())
     */
    public Optional<String> getNextValidatorAddress() {
        List<String> validators = accountRepository.getValidators();
        if (validators.isEmpty()) {
            return Optional.empty();
        }

        int nextHeight = getNextHeight();
        int index = nextHeight % validators.size();
        String validatorAddress = validators.get(index);

        return Optional.of(validatorAddress);
    }

    /**
     * Проверяет, является ли указанный адрес валидатором текущего хода.
     */
    public boolean isCurrentValidator(String address) {
        return getNextValidatorAddress()
                .map(addr -> addr.equals(address))
                .orElse(false);
    }

    /**
     * Возвращает валидатора для следующего блока (с полной информацией об аккаунте).
     */
    public Optional<Account> getNextValidator() {
        return getNextValidatorAddress()
                .flatMap(accountRepository::findByAddress)
                .filter(Account::isActive);
    }

    private int getNextHeight() {
        return blockChainService.getLatestBlock() != null
                ? blockChainService.getLatestBlock().getHeight() + 1
                : 0;
    }
}
