package ru.vkr.blockchain.domain.model;

import jakarta.persistence.*;
import lombok.*;
import ru.vkr.blockchain.domain.model.enums.AccountRole;

import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
public class Account implements Serializable{

    @Serial
    private static final long serialVersionUID = 1L;

    private String address; // часть публичного ключа

    private String publicKey;

    private List<AccountRole> accountRoles;

    private boolean isActive;

    private String createdByAddress;

    private LocalDateTime createdAt;

    public Account(String address, String publicKey, String createdByAddress) {
        this.address = address;
        this.publicKey = publicKey;
        this.accountRoles = new ArrayList<>();
        this.accountRoles.add(AccountRole.USER);
        this.isActive = true;
        this.createdByAddress = createdByAddress;
        this.createdAt = LocalDateTime.now();
    }

    public byte[] toBytes() throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(this);
            return bos.toByteArray();
        }
    }

    public static Account fromBytes(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (Account) ois.readObject();
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
