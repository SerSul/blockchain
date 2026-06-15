package ru.vkr.blockchain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.vkr.blockchain.annotations.RequireRole;
import ru.vkr.blockchain.domain.model.enums.AccountRole;
import ru.vkr.blockchain.domain.model.enums.TransactionType;
import ru.vkr.blockchain.dto.CreateTransactionRequest;
import ru.vkr.blockchain.dto.StoreDataPayload;
import ru.vkr.blockchain.exception.storage.FileNotFoundException;
import ru.vkr.blockchain.exception.transaction.TransactionValidationException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final BlockChainService blockChainService;
    private final MinioFileStorageService minioFileStorageService;
    private final StoreDataPayloadValidator storeDataPayloadValidator;

    @RequireRole({AccountRole.USER, AccountRole.VALIDATOR})
    public String storeFile(CreateTransactionRequest request, MultipartFile file) {
        if (request.getTransactionType() != TransactionType.STORE_FILE) {
            throw new TransactionValidationException("transaction_type must be STORE_FILE");
        }

        StoreDataPayload payload = storeDataPayloadValidator.validateUpload(file, request.getPayload());

        String contentType = request.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        }
        request.setContentType(contentType);

        minioFileStorageService.upload(file, payload.resolveObjectKey(), contentType);
        String transactionId = blockChainService.storeFile(request);
        log.info("File registered in pending pool: fileHash={}, fileName={}, txId={}",
                payload.getFileHash(), payload.getFileName(), transactionId);
        return transactionId;
    }

    public StoredFileDownload download(String fileHash) {
        if (fileHash == null || fileHash.isBlank()) {
            throw new TransactionValidationException("fileHash must not be empty");
        }
        try {
            var stored = minioFileStorageService.download(fileHash);
            return new StoredFileDownload(stored.stream(), stored.size(), stored.contentType(), fileHash);
        } catch (FileNotFoundException e) {
            throw e;
        }
    }

    public record StoredFileDownload(
            java.io.InputStream stream,
            long size,
            String contentType,
            String fileHash
    ) {
    }
}
