package ru.vkr.blockchain.api;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {

    @NotBlank(message = "Creator public key is required")
    private String creatorPublicKey;

    @NotBlank(message = "New user public key is required")
    private String newUserPublicKey;

    @NotBlank(message = "Signature is required")
    private String signature;
}
