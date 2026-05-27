package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NetworkStatusDto {

    @JsonProperty("latest_height")
    private int latestHeight;

    @JsonProperty("pending_transactions")
    private long pendingTransactions;

    @JsonProperty("validator_count")
    private int validatorCount;

    @JsonProperty("bootstrap_validator_count")
    private int bootstrapValidatorCount;

    @JsonProperty("next_validator_address")
    private String nextValidatorAddress;

    @JsonProperty("next_block_height")
    private int nextBlockHeight;
}
