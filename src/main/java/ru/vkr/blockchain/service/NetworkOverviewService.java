package ru.vkr.blockchain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.domain.model.Account;
import ru.vkr.blockchain.dto.AccountSyncDto;
import ru.vkr.blockchain.dto.NetworkJoinSnapshotDto;
import ru.vkr.blockchain.dto.NetworkStatusDto;
import ru.vkr.blockchain.dto.NetworkValidatorDto;
import ru.vkr.blockchain.dto.explorer.AccountExplorerRow;
import ru.vkr.blockchain.repository.AccountRepository;
import ru.vkr.blockchain.repository.PendingTransactionRepository;
import ru.vkr.blockchain.service.domain.BlockService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NetworkOverviewService {

    private final AccountRepository accountRepository;
    private final ValidatorSelectionService validatorSelectionService;
    private final BlockService blockService;
    private final PendingTransactionRepository pendingTransactionRepository;

    public List<NetworkValidatorDto> getValidators() {
        List<String> roundRobin = accountRepository.getValidators();
        List<String> bootstrap = accountRepository.getBootstrapValidators();
        List<NetworkValidatorDto> result = new ArrayList<>();

        for (int i = 0; i < roundRobin.size(); i++) {
            String address = roundRobin.get(i);
            Optional<Account> account = accountRepository.findByAddress(address);
            result.add(new NetworkValidatorDto(
                    address,
                    account.map(Account::getAccountRoles).orElse(List.of()),
                    account.map(Account::isActive).orElse(false),
                    bootstrap.contains(address),
                    i
            ));
        }
        return result;
    }

    public NetworkStatusDto getStatus() {
        int latestHeight = blockService.findLatest().map(b -> b.getHeight()).orElse(-1);
        int nextHeight = latestHeight + 1;
        String nextValidator = validatorSelectionService.getNextValidatorAddress().orElse(null);
        List<String> validators = accountRepository.getValidators();

        return new NetworkStatusDto(
                latestHeight,
                pendingTransactionRepository.findAll().size(),
                validators.size(),
                accountRepository.getBootstrapValidators().size(),
                nextValidator,
                nextHeight
        );
    }

    public NetworkJoinSnapshotDto buildJoinSnapshot() {
        return new NetworkJoinSnapshotDto(
                accountRepository.getValidators(),
                accountRepository.getBootstrapValidators(),
                accountRepository.findAll().stream()
                        .map(a -> new AccountSyncDto(
                                a.getAddress(),
                                a.getPublicKey(),
                                a.getAccountRoles(),
                                a.isActive()))
                        .toList()
        );
    }

    public List<AccountExplorerRow> getAccountsForExplorer() {
        List<String> roundRobin = accountRepository.getValidators();
        List<String> bootstrap = accountRepository.getBootstrapValidators();

        return accountRepository.findAll().stream()
                .sorted(Comparator.comparing(Account::getAddress))
                .map(account -> new AccountExplorerRow(
                        account.getAddress(),
                        account.getAccountRoles(),
                        account.isActive(),
                        roundRobin.contains(account.getAddress()),
                        bootstrap.contains(account.getAddress())
                ))
                .toList();
    }
}
