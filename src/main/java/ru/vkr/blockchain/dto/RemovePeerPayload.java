package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemovePeerPayload {

    @NotBlank(message = "Peer URL is required")
    @JsonProperty("peer_url")
    private String peerUrl;
}
