package ru.vkr.blockchain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vkr.blockchain.annotations.RequireRole;
import ru.vkr.blockchain.config.BlockchainProperties;
import ru.vkr.blockchain.domain.entity.FileTraceEvent;
import ru.vkr.blockchain.domain.entity.TransactionMetadata;
import ru.vkr.blockchain.domain.model.Transaction;
import ru.vkr.blockchain.domain.model.enums.AccountRole;
import ru.vkr.blockchain.domain.model.enums.FileTraceEventType;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.dto.FileTraceDto;
import ru.vkr.blockchain.dto.FileTraceEventDto;
import ru.vkr.blockchain.dto.RecordDownloadRequest;
import ru.vkr.blockchain.dto.StoreDataPayload;
import ru.vkr.blockchain.exception.transaction.TransactionValidationException;
import ru.vkr.blockchain.repository.PendingTransactionRepository;
import ru.vkr.blockchain.repository.entity.FileTraceEventRepository;
import ru.vkr.blockchain.repository.entity.TransactionMetadataRepository;
import ru.vkr.blockchain.service.domain.BlockService;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Off-chain журнал скачиваний (PostgreSQL). Версии файла — в payload STORE_FILE (previous_transaction_id).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileTraceService {

    private final FileTraceEventRepository fileTraceEventRepository;
    private final TransactionMetadataRepository transactionMetadataRepository;
    private final PendingTransactionRepository pendingTransactionRepository;
    private final BlockService blockService;
    private final FileVersionService fileVersionService;
    private final CryptoService cryptoService;
    private final BlockchainProperties blockchainProperties;
    private final ObjectMapper objectMapper;

    @RequireRole({AccountRole.USER, AccountRole.VALIDATOR})
    @Transactional
    public FileTraceEventDto recordDownload(RecordDownloadRequest request) {
        validateFileHash(request.getFileHash());
        String payload = buildDownloadPayload(request.getFileHash(), request.getSourceTransactionId());
        verifySignature(payload, request.getSignature(), request.getCreatorPublicKey());
        if (request.getSourceTransactionId() != null && !request.getSourceTransactionId().isBlank()) {
            validateStoreFileReference(request.getSourceTransactionId(), request.getFileHash());
        }
        String actor = cryptoService.generateAddress(request.getCreatorPublicKey());
        return saveDownload(request.getFileHash(), actor, request.getSourceTransactionId());
    }

    public FileTraceDto getFileTrace(String fileHash) {
        validateFileHash(fileHash);
        List<FileTraceEventDto> downloads = fileTraceEventRepository
                .findByFileHashAndEventTypeOrderByRecordedAtAsc(fileHash, FileTraceEventType.DOWNLOAD)
                .stream().map(this::toDto).toList();
        return FileTraceDto.builder()
                .fileHash(fileHash)
                .downloads(downloads)
                .versionChain(fileVersionService.getVersionChain(fileHash))
                .build();
    }

    public List<FileTraceEventDto> listRecent(int limit) {
        int pageSize = Math.min(Math.max(limit, 1), 200);
        return fileTraceEventRepository.findAllByOrderByRecordedAtDesc(PageRequest.of(0, pageSize))
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<FileTraceEventDto> listEventsSince(LocalDateTime since, int limit) {
        LocalDateTime effectiveSince = since != null ? since : LocalDateTime.of(1970, 1, 1, 0, 0);
        int pageSize = Math.min(Math.max(limit, 1), 500);
        return fileTraceEventRepository
                .findByRecordedAtAfterOrderByRecordedAtAsc(effectiveSince, PageRequest.of(0, pageSize))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public int importEvents(List<FileTraceEventDto> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }
        int imported = 0;
        for (FileTraceEventDto dto : events) {
            if (dto.getId() == null || dto.getId().isBlank() || fileTraceEventRepository.existsById(dto.getId())) {
                continue;
            }
            FileTraceEvent event = new FileTraceEvent();
            event.setId(dto.getId());
            event.setEventType(dto.getEventType());
            event.setFileHash(dto.getFileHash());
            event.setActorAddress(dto.getActorAddress());
            event.setTransactionId(dto.getTransactionId());
            event.setRecordedAt(dto.getRecordedAt() != null ? dto.getRecordedAt() : LocalDateTime.now());
            event.setOriginNode(dto.getOriginNode() != null ? dto.getOriginNode() : "unknown");
            fileTraceEventRepository.save(event);
            imported++;
        }
        if (imported > 0) {
            log.info("Imported {} off-chain download events from peer", imported);
        }
        return imported;
    }

    public Optional<String> extractFileHashFromStorePayload(String payloadJson) {
        return parseStorePayload(payloadJson).map(StoreDataPayload::getFileHash).filter(h -> !h.isBlank());
    }

    public Optional<String> extractPreviousTransactionId(String payloadJson) {
        return parseStorePayload(payloadJson).map(StoreDataPayload::getPreviousTransactionId).filter(id -> !id.isBlank());
    }

    private Optional<StoreDataPayload> parseStorePayload(String payloadJson) {
        try {
            return Optional.of(objectMapper.readValue(payloadJson, StoreDataPayload.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public LocalDateTime syncCursorSince() {
        return fileTraceEventRepository.findLatestRecordedAt()
                .map(t -> t.minusSeconds(1))
                .orElse(LocalDateTime.of(1970, 1, 1, 0, 0));
    }

    private FileTraceEventDto saveDownload(String fileHash, String actor, String sourceTransactionId) {
        FileTraceEvent event = new FileTraceEvent();
        event.setEventType(FileTraceEventType.DOWNLOAD);
        event.setFileHash(fileHash);
        event.setActorAddress(actor);
        event.setTransactionId(sourceTransactionId);
        event.setOriginNode(blockchainProperties.getNodeId());
        fileTraceEventRepository.save(event);
        return toDto(event);
    }

    private void validateStoreFileReference(String transactionId, String fileHash) {
        Optional<TransactionMetadata> confirmed = transactionMetadataRepository.findById(transactionId);
        if (confirmed.isPresent()) {
            TransactionMetadata meta = confirmed.get();
            if (meta.getTransactionType() != TransactionType.STORE_FILE) {
                throw new TransactionValidationException("source_transaction_id must be STORE_FILE");
            }
            String metaHash = meta.getFileHash();
            if (metaHash != null && !metaHash.equalsIgnoreCase(fileHash)) {
                throw new TransactionValidationException("source_transaction_id fileHash does not match");
            }
            if (metaHash == null) {
                String payload = loadConfirmedPayload(meta);
                if (payload != null) {
                    validateFileHashInPayload(meta.getId(), fileHash, payload);
                }
            }
            return;
        }
        Transaction pending = pendingTransactionRepository.findAll().stream()
                .filter(tx -> tx.getId().equals(transactionId))
                .findFirst()
                .orElseThrow(() -> new TransactionValidationException(
                        "source_transaction_id not found: " + transactionId));
        if (pending.getTransactionType() != TransactionType.STORE_FILE) {
            throw new TransactionValidationException("source_transaction_id must be STORE_FILE");
        }
        validateFileHashInPayload(transactionId, fileHash, pending.getPayload());
    }

    private void validateFileHashInPayload(String transactionId, String expectedHash, String payloadJson) {
        String fromPayload = parseStorePayload(payloadJson)
                .map(StoreDataPayload::getFileHash)
                .orElse(null);
        if (fromPayload == null || !fromPayload.equalsIgnoreCase(expectedHash)) {
            throw new TransactionValidationException("source_transaction_id fileHash does not match");
        }
    }

    private String loadConfirmedPayload(TransactionMetadata meta) {
        if (meta.getBlockHash() == null) {
            return null;
        }
        return blockService.findByHash(meta.getBlockHash())
                .flatMap(block -> block.getTransactions().stream()
                        .filter(tx -> tx.getId().equals(meta.getId()))
                        .findFirst()
                        .map(Transaction::getPayload))
                .orElse(null);
    }

    private void verifySignature(String payload, String signature, String creatorPublicKey) {
        if (!cryptoService.checkSignatureValid(payload, signature, creatorPublicKey)) {
            throw new SecurityException("Invalid signature for trace request");
        }
    }

    private void validateFileHash(String fileHash) {
        if (fileHash == null || fileHash.length() != 64) {
            throw new TransactionValidationException("file_hash must be SHA-256 hex (64 characters)");
        }
    }

    private String buildDownloadPayload(String fileHash, String sourceTransactionId) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("file_hash", fileHash);
        if (sourceTransactionId != null && !sourceTransactionId.isBlank()) {
            map.put("source_transaction_id", sourceTransactionId);
        }
        return compactPayload(map);
    }

    private String compactPayload(Map<String, String> fields) {
        try {
            return objectMapper.writeValueAsString(fields)
                    .replace(": ", ":")
                    .replace(", ", ",");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize trace payload", e);
        }
    }

    private FileTraceEventDto toDto(FileTraceEvent event) {
        return FileTraceEventDto.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .fileHash(event.getFileHash())
                .actorAddress(event.getActorAddress())
                .transactionId(event.getTransactionId())
                .recordedAt(event.getRecordedAt())
                .originNode(event.getOriginNode())
                .build();
    }
}
