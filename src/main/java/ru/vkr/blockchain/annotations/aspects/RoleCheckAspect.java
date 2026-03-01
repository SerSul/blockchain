package ru.vkr.blockchain.annotations.aspects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import ru.vkr.blockchain.annotations.RequireRole;
import ru.vkr.blockchain.dto.CreateTransactionRequest;
import ru.vkr.blockchain.domain.model.enums.AccountRole;
import ru.vkr.blockchain.repository.AccountRepository;
import ru.vkr.blockchain.service.CryptoService;
import ru.vkr.blockchain.service.domain.BlockService;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RoleCheckAspect {
    private final AccountRepository accountRepository;
    private final CryptoService cryptoService;
    private final BlockService blockService;

    @Before("@annotation(ru.vkr.blockchain.annotations.RequireRole)")
    public void checkRole(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireRole requireRole = method.getAnnotation(RequireRole.class);

        if (requireRole == null) {
            return;
        }

        if ("createAccount".equals(method.getName())
                && accountRepository.findAll().isEmpty()
                && blockService.findLatest().isEmpty()) {
            return;
        }

        Object[] args = joinPoint.getArgs();
        String creatorPublicKey = extractCreatorPublicKey(args);

        if (creatorPublicKey == null) {
            throw new SecurityException("Creator public key not found in request");
        }

        String creatorAddress = cryptoService.generateAddress(creatorPublicKey);
        var creator = accountRepository.findByAddress(creatorAddress)
                .orElseThrow(() -> new SecurityException(
                        "Account not found: " + creatorAddress));

        Set<AccountRole> userRoles = Set.copyOf(creator.getAccountRoles());
        Set<AccountRole> requiredRoles = Arrays.stream(requireRole.value())
                .collect(Collectors.toSet());

        boolean hasAccess;
        if (requireRole.requireAll()) {
            hasAccess = userRoles.containsAll(requiredRoles);
        } else {
            hasAccess = userRoles.stream().anyMatch(requiredRoles::contains);
        }

        if (!hasAccess) {
            throw new SecurityException(
                    String.format("Access denied. Required roles: %s, User roles: %s",
                            requiredRoles, userRoles));
        }
    }

    private String extractCreatorPublicKey(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof CreateTransactionRequest) {
                return ((CreateTransactionRequest) arg).getCreatorPublicKey();
            }
        }
        return null;
    }
}
