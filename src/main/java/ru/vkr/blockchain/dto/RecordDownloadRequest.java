package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecordDownloadRequest {

    @NotBlank
    @JsonProperty("creator_public_key")
    private String creatorPublicKey;

    @NotBlank
    @JsonProperty("file_hash")
    private String fileHash;

    @JsonProperty("source_transaction_id")
    private String sourceTransactionId;

    @NotBlank
    private String signature;
}
