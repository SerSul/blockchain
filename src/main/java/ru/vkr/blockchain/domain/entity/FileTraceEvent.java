package ru.vkr.blockchain.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.vkr.blockchain.domain.model.enums.FileTraceEventType;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "file_trace_events", indexes = {
        @Index(name = "idx_trace_file_hash", columnList = "file_hash"),
        @Index(name = "idx_trace_recorded_at", columnList = "recorded_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileTraceEvent {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 32, nullable = false)
    private FileTraceEventType eventType;

    @Column(name = "file_hash", length = 64, nullable = false)
    private String fileHash;

    @Column(name = "actor_address", length = 66, nullable = false)
    private String actorAddress;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "origin_node", length = 128, nullable = false)
    private String originNode;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }
}
