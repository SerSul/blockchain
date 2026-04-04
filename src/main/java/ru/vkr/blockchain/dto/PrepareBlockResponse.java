package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ответ эндпоинта подготовки блока: хеш для подписи и данные для запроса createBlock.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrepareBlockResponse {

    /** Хеш блока — подписать этим значением (validator_signature = sign(hash_to_sign)). */
    @JsonProperty("hash_to_sign")
    private String hashToSign;

    /** Время, использованное при расчёте хеша. Передать тем же в createBlock. */
    private LocalDateTime timestamp;

    /** Высота создаваемого блока. */
    private Integer height;

    /** ID транзакций в порядке включения в блок. Передать тем же в createBlock.transaction_ids. */
    @JsonProperty("transaction_ids")
    private List<String> transactionIds;
}
