package ru.vkr.blockchain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "blockchain.bootstrap")
@Data
public class BlockchainBootstrapProperties {

    private List<String> peers = new ArrayList<>();
    private String adminPublicKey;
    private boolean syncEnabled = true;
    private int syncBatchSize = 50;
}
