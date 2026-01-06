package com.grash.service;

import com.grash.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SecretEncryptionService {
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${LEXWARE_ENCRYPTION_KEY:${INTEGRATIONS_LEXWARE_ENCRYPTION_KEY:${integrations.lexware.encryption-key:}}}")
    private String encryptionKey;

    public String encrypt(String plainText) {
        validateKey();
        Objects.requireNonNull(plainText, "Secret to encrypt cannot be null");
        try {
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] cipherMessage = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();
            return Base64.getEncoder().encodeToString(cipherMessage);
        } catch (GeneralSecurityException e) {
            throw new CustomException("Unable to encrypt Lexware secret", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String decrypt(String cipherText) {
        validateKey();
        try {
            byte[] cipherMessage = Base64.getDecoder().decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(cipherMessage);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new CustomException("Unable to decrypt Lexware secret", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateKey() {
        if (!StringUtils.hasText(encryptionKey)) {
            encryptionKey = System.getenv("LEXWARE_ENCRYPTION_KEY");
        }
        if (!StringUtils.hasText(encryptionKey)) {
            encryptionKey = System.getenv("INTEGRATIONS_LEXWARE_ENCRYPTION_KEY");
        }
        if (!StringUtils.hasText(encryptionKey)) {
            encryptionKey = System.getProperty("LEXWARE_ENCRYPTION_KEY");
        }
        if (!StringUtils.hasText(encryptionKey)) {
            encryptionKey = System.getProperty("INTEGRATIONS_LEXWARE_ENCRYPTION_KEY");
        }
        if (!StringUtils.hasText(encryptionKey)) {
            encryptionKey = System.getenv("JWT_SECRET_KEY");
        }
        if (!StringUtils.hasText(encryptionKey)) {
            encryptionKey = System.getProperty("security.jwt.token.secret-key");
        }
        if (StringUtils.hasText(encryptionKey)) {
            encryptionKey = encryptionKey.trim();
        }
        if (!StringUtils.hasText(encryptionKey)) {
            throw new CustomException("Lexware encryption key is missing (set LEXWARE_ENCRYPTION_KEY, INTEGRATIONS_LEXWARE_ENCRYPTION_KEY or reuse JWT_SECRET_KEY)", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private SecretKey getSecretKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hashed, "AES");
        } catch (GeneralSecurityException e) {
            throw new CustomException("Unable to derive Lexware encryption key", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
