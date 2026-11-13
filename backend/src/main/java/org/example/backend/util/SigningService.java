package org.example.backend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 本地托管身份工具：注册时生成 RSA 密钥对，私钥用 AES-GCM 加密落库。
 *
 * <p>在合约账户方案接入前，公钥派生的地址作为平台内可验证的创作者/协作者身份；
 * 合约账户接通后只需替换地址来源，签名仍使用同一密钥对。
 */
@Component
public class SigningService {

    private static final Logger log = LoggerFactory.getLogger(SigningService.class);
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int AES_KEY_BYTES = 16;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    @Value("${app.identity.secret:aic-local-identity-secret-change-me}")
    private String cipherSecret;

    private final SecureRandom secureRandom = new SecureRandom();

    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, secureRandom);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("RSA key generation failed", e);
        }
    }

    public String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public String deriveAddress(Long userId, String publicKeyEncoded) {
        String material = "aic-identity|" + (userId == null ? "pending" : userId) + "|" + publicKeyEncoded;
        String digest = HashUtil.sha256(material);
        return "0x" + digest.substring(0, 40);
    }

    public String encryptPrivateKey(byte[] privateKeyEncoded) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(privateKeyEncoded);
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Private key encryption failed", e);
        }
    }

    public PrivateKey decryptPrivateKey(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            throw new IllegalStateException("Private key is missing");
        }
        try {
            String[] parts = encrypted.split("\\.");
            if (parts.length != 2) {
                throw new IllegalStateException("Private key cipher text is malformed");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] data = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] pkcs8 = cipher.doFinal(data);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception e) {
            throw new IllegalStateException("Private key decryption failed", e);
        }
    }

    public String sign(String privateKeyEncoded, String payload) {
        try {
            PrivateKey privateKey = decryptPrivateKey(privateKeyEncoded);
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Signature failed", e);
        }
    }

    public boolean verify(String publicKeyEncoded, String payload, String signatureValue) {
        if (publicKeyEncoded == null || signatureValue == null || signatureValue.isEmpty()) {
            return false;
        }
        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyEncoded)));
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureValue));
        } catch (Exception e) {
            log.warn("Signature verification failed", e);
            return false;
        }
    }

    private SecretKeySpec aesKey() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(cipherSecret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, 0, AES_KEY_BYTES, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("AES key derivation failed", e);
        }
    }
}
