package ru.vkr.blockchain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "blockchain")
@Data
public class BlockchainProperties {

    private String nodeId = "local";
}
