package ru.vkr.blockchain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "leveldb")
@Data
public class LevelDBProperties {
    private String path = "./data/leveldb";
    private boolean createIfMissing = true;
    private long cacheSize = 104857600L;
}
