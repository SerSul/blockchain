package ru.vkr.blockchain.domain.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.vkr.blockchain.domain.model.enums.BlockStatus;

import java.io.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
public class Block implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String previousHash;
    private String currentHash;
    private String merkleRoot; // Хеш суммы хешей всех транзакций
    private LocalDateTime timestamp;
    private String validatorAddress;
    private String validatorSignature;
    private Integer height;
    private List<Transaction> transactions = new ArrayList<>();
    private BlockStatus status;

    public Block(Integer height, String previousHash) {
        this.height = height;
        this.previousHash = previousHash;
        this.timestamp = LocalDateTime.now();
        this.status = BlockStatus.PENDING;
        this.transactions = new ArrayList<>();
    }

    /**
     * Вычисляет хэш блока
     */
    public String calculateHash() {
        String data = previousHash + merkleRoot + timestamp + validatorAddress + height;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate hash", e);
        }
    }

    /**
     * Добавляет транзакцию в блок
     */
    public void addTransaction(Transaction transaction) {
        if (transactions == null) transactions = new ArrayList<>();
        transactions.add(transaction);
    }

    /**
     * Валидация блока
     */
    public boolean validate() {
        String calculatedHash = calculateHash();
        if (!calculatedHash.equals(currentHash)) {
            log.warn("Block validation failed: hash mismatch, expected={}, actual={}, height={}",
                    currentHash, calculatedHash, height);
            return false;
        }

        if (transactions != null) {
            for (Transaction tx : transactions) {
                if (!tx.isValid()) {
                    log.warn("Block validation failed: invalid transaction id={}, height={}",
                            tx != null ? tx.getId() : null, height);
                    return false;
                }
            }
        }

        return true;
    }

    public byte[] toBytes() throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(this);
            return bos.toByteArray();
        }
    }

    public static Block fromBytes(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (Block) ois.readObject();
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
