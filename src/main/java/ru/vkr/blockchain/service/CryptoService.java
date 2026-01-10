package ru.vkr.blockchain.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.jcajce.provider.digest.SHA256;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
public class CryptoService {

    public CryptoService() {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Генерирует адрес из публичного ключа (0x + первые 40 символов SHA-256)
     */
    public String generateAddress(PublicKey publicKey) {
        byte[] publicKeyBytes = publicKey.getEncoded();
        String hash = calculateSHA256(publicKeyBytes);
        String addressPart = hash.substring(0, 40);

        return "0x" + addressPart;
    }

    /**
     * Генерирует адрес из публичного ключа (Base64 строка)
     * @param publicKeyBase64 публичный ключ в Base64
     * @return адрес вида 0x...
     */
    public String generateAddress(String publicKeyBase64)  {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            throw new RuntimeException("Public key cannot be null or empty");
        }

        try {
            PublicKey publicKey = decodePublicKey(publicKeyBase64);
            return generateAddress(publicKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate address from public key: " + e.getMessage(), e);
        }
    }

    /**
     * Проверить подпись публичным ключом
     */
    public boolean verify(String data, String signature, PublicKey publicKey) {
        try {
            Signature sig = Signature.getInstance("SHA256withECDSA", "BC");
            sig.initVerify(publicKey);
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getDecoder().decode(signature);

            return sig.verify(signatureBytes);
        } catch (Exception e) {
            log.error("Signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Вычислить SHA-256 хеш строки
     * @return Hex строка (64 символа)
     */
    public String calculateSHA256(String input) {
        return DigestUtils.sha256Hex(input);
    }

    /**
     * Вычислить SHA-256 хеш байтов
     * @return Hex строка (64 символа)
     */
    public String calculateSHA256(byte[] input) {
        return DigestUtils.sha256Hex(input);
    }

    /**
     * Вычислить Merkle Root из списка хешей
     */
    public String calculateMerkleRoot(List<String> hashes) {
        if (CollectionUtils.isEmpty(hashes)) {
            return calculateSHA256(""); // Пустой блок
        }

        if (hashes.size() == 1) {
            return hashes.getFirst(); // Один хеш = он и есть корень
        }

        List<String> currentLevel = new ArrayList<>(hashes);

        if (currentLevel.size() % 2 != 0) {
            currentLevel.add(currentLevel.getLast());
        }

        while (currentLevel.size() > 1) {
            List<String> nextLevel = new ArrayList<>();

            for (int i = 0; i < currentLevel.size(); i += 2) {
                String left = currentLevel.get(i);
                String right = currentLevel.get(i + 1);

                String combined = calculateSHA256(left + right);
                nextLevel.add(combined);
            }

            if (nextLevel.size() > 1 && nextLevel.size() % 2 != 0) {
                nextLevel.add(nextLevel.getLast());
            }

            currentLevel = nextLevel;
        }

        return currentLevel.getFirst();
    }

    /**
     * Декодировать публичный ключ из Base64
     */
    public PublicKey decodePublicKey(String encoded) {
        byte[] decoded = Base64.getDecoder().decode(encoded);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");

            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Unable to decode public key: " + e.getMessage());
        }
    }

    public boolean checkSignatureValid(String data, String signature, String creatorPublicKeyBase64) {
        try {
            var creatorPublicKey = decodePublicKey(creatorPublicKeyBase64);
            return verify(data, signature, creatorPublicKey);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
