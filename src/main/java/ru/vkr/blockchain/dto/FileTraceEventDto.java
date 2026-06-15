package ru.vkr.blockchain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.vkr.blockchain.domain.model.enums.FileTraceEventType;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileTraceEventDto {

    private String id;
    @JsonProperty("event_type")
    private FileTraceEventType eventType;
    @JsonProperty("file_hash")
    private String fileHash;
    @JsonProperty("actor_address")
    private String actorAddress;
    @JsonProperty("transaction_id")
    private String transactionId;
    @JsonProperty("recorded_at")
    private LocalDateTime recordedAt;
    @JsonProperty("origin_node")
    private String originNode;
}
