package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBlockRequest {

    @NotBlank(message = "Validator public key is required")
    @JsonProperty("validator_public_key")
    private String validatorPublicKey;

    @NotBlank(message = "Validator signature is required")
    @JsonProperty("validator_signature")
    private String validatorSignature;

    @NotNull(message = "Timestamp is required")
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("transaction_ids")
    private List<String> transactionIds;
}
