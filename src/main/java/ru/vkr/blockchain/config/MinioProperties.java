package ru.vkr.blockchain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    private boolean enabled = true;
    private String endpoint = "http://localhost:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String bucket = "blockchain-files";
    private long maxFileSizeBytes = 10 * 1024 * 1024;
}
