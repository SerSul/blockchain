package ru.vkr.blockchain.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import ru.vkr.blockchain.domain.model.enums.BlockStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "blocks_metadata", indexes = {
        @Index(name = "idx_height", columnList = "height"),
        @Index(name = "idx_block_status", columnList = "status"),
        @Index(name = "idx_validator", columnList = "validator_address"),
        @Index(name = "idx_block_timestamp", columnList = "timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockMetadata {

    @Id
    @Column(name = "hash", nullable = false)
    private String hash;

    @Column(name = "previous_hash")
    private String previousHash;

    @Column(name = "height", nullable = false, unique = true)
    private Integer height;

    @Column(name = "merkle_root", nullable = false)
    private String merkleRoot;

    @Column(name = "validator_address", nullable = false)
    private String validatorAddress;

    @Column(name = "validator_signature", columnDefinition = "TEXT", nullable = false)
    private String validatorSignature;

    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BlockStatus status;

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
            leveldbKey = "block:" + hash;
        }
    }
}
