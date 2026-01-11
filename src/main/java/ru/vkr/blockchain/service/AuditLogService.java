package ru.vkr.blockchain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.vkr.blockchain.repository.entity.AuditLogRepository;

@RequiredArgsConstructor
@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;
}
