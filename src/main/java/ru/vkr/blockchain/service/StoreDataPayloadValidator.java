package ru.vkr.blockchain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import ru.vkr.blockchain.config.MinioProperties;
import ru.vkr.blockchain.dto.StoreDataPayload;
import ru.vkr.blockchain.exception.transaction.TransactionValidationException;

@Component
@RequiredArgsConstructor
public class StoreDataPayloadValidator {

    private final ObjectMapper objectMapper;
    private final MinioProperties minioProperties;
    private final CryptoService cryptoService;

    public StoreDataPayload parse(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, StoreDataPayload.class);
        } catch (Exception e) {
            throw new TransactionValidationException("Invalid STORE_FILE payload JSON: " + e.getMessage());
        }
    }

    public void validateFilePayload(String payloadJson) {
        validateStructure(parse(payloadJson));
    }

    public StoreDataPayload validateUpload(MultipartFile file, String payloadJson) {
        StoreDataPayload payload = parse(payloadJson);
        validateStructure(payload);
        validateUploadedFile(file, payload);
        return payload;
    }

    private void validateStructure(StoreDataPayload payload) {
        if (!payload.isFileReference()) {
            throw new TransactionValidationException("File payload must contain fileHash, fileName and size");
        }
        if (payload.getFileName() == null || payload.getFileName().isBlank()) {
            throw new TransactionValidationException("fileName must not be empty");
        }
        if (payload.getSize() == null || payload.getSize() <= 0) {
            throw new TransactionValidationException("size must be positive");
        }
        if (payload.getSize() > minioProperties.getMaxFileSizeBytes()) {
            throw new TransactionValidationException("File size exceeds limit: " + minioProperties.getMaxFileSizeBytes());
        }
        if (payload.getFileHash().length() != 64) {
            throw new TransactionValidationException("fileHash must be SHA-256 hex (64 characters)");
        }
    }

    private void validateUploadedFile(MultipartFile file, StoreDataPayload payload) {
        if (file == null || file.isEmpty()) {
            throw new TransactionValidationException("File must not be empty");
        }
        if (file.getSize() != payload.getSize()) {
            throw new TransactionValidationException("Uploaded file size does not match payload.size");
        }
        try {
            String actualHash = cryptoService.calculateSHA256(file.getBytes());
            if (!actualHash.equalsIgnoreCase(payload.getFileHash())) {
                throw new TransactionValidationException("Uploaded file hash does not match payload.fileHash");
            }
        } catch (TransactionValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new TransactionValidationException("Failed to validate uploaded file hash");
        }
    }
}
