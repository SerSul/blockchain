package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrepareBlockRequest {

    /** Опционально: ID транзакций для включения в блок. Если пусто или null — берутся все pending. */
    @JsonProperty("transaction_ids")
    private List<String> transactionIds;
}
