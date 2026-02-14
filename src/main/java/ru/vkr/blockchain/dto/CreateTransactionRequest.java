package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.vkr.blockchain.domain.model.enums.TransactionType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransactionRequest {

    @NotBlank(message = "Creator public key is required")
    @JsonProperty("creator_public_key")
    private String creatorPublicKey;

    @NotNull(message = "Transaction type must not be null")
    @JsonProperty("transaction_type")
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @NotBlank(message = "Payload must not be null")
    private String payload;

    @JsonProperty("content_type")
    private String contentType;

    @NotBlank(message = "Signature is required")
    private String signature;
}
