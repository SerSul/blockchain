package ru.vkr.blockchain.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.vkr.blockchain.config.MinioProperties;
import ru.vkr.blockchain.exception.storage.FileNotFoundException;
import ru.vkr.blockchain.exception.storage.StorageException;

import java.io.InputStream;

@Service
@ConditionalOnBean(MinioClient.class)
@RequiredArgsConstructor
@Slf4j
public class MinioFileStorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public String upload(MultipartFile file, String objectKey, String contentType) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("File is empty");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new StorageException("Object key must not be empty");
        }
        try {
            String resolvedContentType = contentType != null && !contentType.isBlank()
                    ? contentType
                    : "application/octet-stream";
            if (exists(objectKey)) {
                log.debug("MinIO object already exists, skip upload: {}", objectKey);
                return objectKey;
            }
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(objectKey)
                        .stream(inputStream, file.getSize(), -1)
                        .contentType(resolvedContentType)
                        .build());
            }
            log.info("File uploaded to MinIO: bucket={}, objectKey={}, size={}",
                    properties.getBucket(), objectKey, file.getSize());
            return objectKey;
        } catch (Exception e) {
            throw new StorageException("Failed to upload file to MinIO", e);
        }
    }

    public StoredFile download(String objectKey) {
        try {
            var response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
            var stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
            return new StoredFile(response, stat.size(), stat.contentType());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new FileNotFoundException(objectKey);
            }
            throw new StorageException("Failed to download file from MinIO", e);
        } catch (Exception e) {
            throw new StorageException("Failed to download file from MinIO", e);
        }
    }

    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new StorageException("Failed to check file in MinIO", e);
        } catch (Exception e) {
            throw new StorageException("Failed to check file in MinIO", e);
        }
    }

    public record StoredFile(InputStream stream, long size, String contentType) {
    }
}
