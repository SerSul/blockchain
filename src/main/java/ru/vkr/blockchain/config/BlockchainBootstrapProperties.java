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

    /**
     * Начальный список пиров. Добавляются при старте, если список пиров в репозитории пуст.
     */
    private List<String> peers = new ArrayList<>();
}
