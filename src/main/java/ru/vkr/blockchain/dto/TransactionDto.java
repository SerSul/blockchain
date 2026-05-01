package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.vkr.blockchain.domain.model.enums.TransactionStatus;
import ru.vkr.blockchain.domain.model.enums.TransactionType;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDto {

    private String id;
    @JsonProperty("sender_id")
    private String senderId;
    @JsonProperty("transaction_type")
    private TransactionType transactionType;
    private TransactionStatus status;
    @JsonProperty("block_hash")
    private String blockHash;
    @JsonProperty("payload_hash")
    private String payloadHash;
    @JsonProperty("content_type")
    private String contentType;
    @JsonProperty("content_size")
    private Long contentSize;
    private LocalDateTime timestamp;
    private String payload;
    private String signature;
}
