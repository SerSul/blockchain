package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.vkr.blockchain.domain.model.enums.BlockStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockDto {

    private String hash;
    @JsonProperty("previous_hash")
    private String previousHash;
    private Integer height;
    @JsonProperty("merkle_root")
    private String merkleRoot;
    @JsonProperty("validator_address")
    private String validatorAddress;
    @JsonProperty("validator_signature")
    private String validatorSignature;
    @JsonProperty("transaction_count")
    private Integer transactionCount;
    private BlockStatus status;
    private LocalDateTime timestamp;
    private List<TransactionDto> transactions;
}
