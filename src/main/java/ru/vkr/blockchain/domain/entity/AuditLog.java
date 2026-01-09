package ru.vkr.blockchain.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_timestamp", columnList = "timestamp"),
        @Index(name = "idx_actor", columnList = "actor_address")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "entity_type", length = 50, nullable = false)
    private String entityType;

    @Column(name = "entity_id", length = 100, nullable = false)
    private String entityId;

    @Column(name = "action", length = 50, nullable = false)
    private String action;

    @Column(name = "actor_address", length = 66, nullable = false)
    private String actorAddress;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    public AuditLog(String entityType, String entityId, String action, String actorAddress, String details) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.actorAddress = actorAddress;
        this.details = details;
    }
}
