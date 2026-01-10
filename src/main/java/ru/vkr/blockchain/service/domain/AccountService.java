package ru.vkr.blockchain.service.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.vkr.blockchain.domain.model.Account;
import ru.vkr.blockchain.domain.model.enums.AccountRole;
import ru.vkr.blockchain.repository.AccountRepository;
import ru.vkr.blockchain.service.CryptoService;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final CryptoService cryptoService;

    /**
     * Получает аккаунт по адресу
     */
    public Account getAccount(String address) {
        return accountRepository.findByAddress(address)
                .orElseThrow(() -> new RuntimeException("Account not found: " + address));
    }

    /**
     * Создаёт новый аккаунт
     * @param creatorPublicKeyBase64 адрес админа/валидатора
     * @param newUserPublicKeyBase64 публичный ключ нового аккаунта
     */
    public void createAccount(
            String creatorPublicKeyBase64,
            String newUserPublicKeyBase64) throws Exception {

        var creatorAddress = cryptoService.generateAddress(creatorPublicKeyBase64);
        var newUserAddress = cryptoService.generateAddress(newUserPublicKeyBase64);

        Account account = new Account(newUserAddress, newUserPublicKeyBase64, creatorAddress);
        account.setAccountRoles(List.of(AccountRole.USER));
        accountRepository.save(account);
    }

    /**
     * Проверяет существование аккаунта
     */
    public boolean accountExists(String address) {
        return accountRepository.exists(address);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
