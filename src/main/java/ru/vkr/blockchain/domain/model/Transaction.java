package ru.vkr.blockchain.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import ru.vkr.blockchain.domain.model.enums.TransactionStatus;
import ru.vkr.blockchain.domain.model.enums.TransactionType;

import java.io.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class Transaction implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String senderId;
    private TransactionType transactionType;
    private TransactionStatus status;
    private LocalDateTime timestamp;

    private String payload;
    private String payloadHash;
    private String contentType;

    private String signature;

    public Transaction(String senderId, String payload, String contentType, TransactionType transactionType, String signature) {
        this.id = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.payload = payload;
        this.payloadHash = calculatePayloadHash();
        this.contentType = contentType;
        this.transactionType = transactionType;
        this.status = TransactionStatus.PENDING;
        this.timestamp = LocalDateTime.now();
        this.signature = signature;
    }

    /**
     * Вычисляет хэш payload
     */
    private String calculatePayloadHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes());
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate payload hash", e);
        }
    }

    /**
     * Вычисляет хэш транзакции для подписи
     */
    public String calculateHash() {
        String data = id + senderId + payloadHash + timestamp;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate hash", e);
        }
    }

    /**
     * Валидация транзакции
     */
    public boolean isValid() {
        if (payload != null) {
            String calculatedHash = calculatePayloadHash();
            if (calculatedHash == null || !calculatedHash.equals(payloadHash)) {
                return false;
            }
        }

        return signature != null && !signature.isEmpty();
    }

    /**
     * Сериализация в байты для LevelDB
     */
    public byte[] toBytes() throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(this);
            return bos.toByteArray();
        }
    }

    /**
     * Десериализация из байтов
     */
    public static Transaction fromBytes(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (Transaction) ois.readObject();
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
