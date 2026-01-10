package ru.vkr.blockchain.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
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

    /**
     * Генерирует пару ключей ECDSA (secp256k1)
     */
    public KeyPair generateKeyPair() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", new BouncyCastleProvider());

        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");

        keyPairGenerator.initialize(ecSpec, new SecureRandom());

        return keyPairGenerator.generateKeyPair();
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
     * Подписать данные приватным ключом (ECDSA)
     * @return Base64 строка подписи
     */
    public String sign(String data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA", "BC");
        signature.initSign(privateKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));

        byte[] signatureBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signatureBytes);
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
    String calculateSHA256(String input) {
        return DigestUtils.sha256Hex(input);
    }

    /**
     * Вычислить SHA-256 хеш байтов
     * @return Hex строка (64 символа)
     */
    String calculateSHA256(byte[] input) {
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
     * Закодировать публичный ключ в Base64
     */
    String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Закодировать приватный ключ в Base64
     */
    String encodePrivateKey(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * Декодировать публичный ключ из Base64
     */
    PublicKey decodePublicKey(String encoded) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encoded);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");

        return keyFactory.generatePublic(spec);
    }

    /**
     * Декодировать приватный ключ из Base64
     */
    PrivateKey decodePrivateKey(String encoded) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encoded);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");

        return keyFactory.generatePrivate(spec);
    }

    /**
     * Зашифровать приватный ключ с помощью AES
     * @param privateKey приватный ключ
     * @param password пароль для шифрования
     * @return Base64 зашифрованная строка
     */
    String encryptPrivateKey(PrivateKey privateKey, String password) throws Exception;

    /**
     * Расшифровать приватный ключ
     */
    PrivateKey decryptPrivateKey(String encrypted, String password) throws Exception;
}
