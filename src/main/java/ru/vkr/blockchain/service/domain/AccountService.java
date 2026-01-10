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

    private List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

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
     * @param signature подпись команды
     */
    public void createAccount(
            String creatorPublicKeyBase64,
            String newUserPublicKeyBase64,
            String signature) throws Exception {

        var creatorPublicKey = cryptoService.decodePublicKey(creatorPublicKeyBase64);
        var creatorAddress = cryptoService.generateAddress(creatorPublicKey);

        var creatorAccount = accountRepository.findByAddress(creatorAddress)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Creator account not found: " + creatorPublicKeyBase64));

        boolean isAdmin = creatorAccount.getAccountRoles().stream()
                .anyMatch(role -> role == AccountRole.ADMIN || role == AccountRole.VALIDATOR);

        if (!isAdmin) {
            throw new SecurityException(
                    "Only admins/validators can create accounts. User: " + creatorPublicKeyBase64);
        }

        var newUserPublicKey = cryptoService.decodePublicKey(newUserPublicKeyBase64);
        var newUserAddress = cryptoService.generateAddress(newUserPublicKey);

        boolean isSignatureValid = cryptoService.verify(newUserPublicKeyBase64, signature, creatorPublicKey);

        if (!isSignatureValid) {
            throw new SecurityException("Invalid signature for account creation");
        }

        if (accountRepository.findByAddress(newUserAddress).isPresent()) {
            throw new IllegalArgumentException(
                    "Account with this public key already exists: " + newUserAddress);
        }

        Account account = new Account(newUserAddress, newUserPublicKeyBase64);
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
