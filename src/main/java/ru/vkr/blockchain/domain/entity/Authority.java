package ru.vkr.blockchain.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "authorities", indexes = {
        @Index(name = "idx_is_active", columnList = "is_active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Authority {

    @Id
    @Column(name = "address", length = 66, nullable = false)
    private String address;

    @Column(name = "public_key", columnDefinition = "TEXT", nullable = false)
    private String publicKey;

    @Column(name = "private_key_encrypted", columnDefinition = "TEXT")
    private String privateKeyEncrypted;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    @Column(name = "added_by", length = 66, nullable = false)
    private String addedBy;

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
    }

    /**
     * Активирует валидатора
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * Деактивирует валидатора
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Проверка, может ли валидатор создавать блоки
     */
    public boolean canValidate() {
        return isActive != null && isActive;
    }
}
