package ru.vkr.blockchain.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import ru.vkr.blockchain.domain.model.enums.TransactionStatus;
import ru.vkr.blockchain.domain.model.enums.TransactionType;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions_metadata", indexes = {
        @Index(name = "idx_sender", columnList = "sender_id"),
        @Index(name = "idx_block_hash", columnList = "block_hash"),
        @Index(name = "idx_transaction_status", columnList = "status"),
        @Index(name = "idx_transaction_timestamp", columnList = "timestamp"),
        @Index(name = "idx_content_type", columnList = "content_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMetadata {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "sender_id", nullable = false)
    private String senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "block_hash")
    private String blockHash; // В каком блоке находится

    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "content_size")
    private Long contentSize;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "leveldb_key", nullable = false)
    private String leveldbKey; // Ключ для поиска в LevelDB

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (leveldbKey == null) {
            leveldbKey = "tx:" + id;
        }
    }
}
