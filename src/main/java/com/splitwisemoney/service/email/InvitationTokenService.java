package com.splitwisemoney.service.email;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class InvitationTokenService {

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a 256-bit cryptographically secure random invitation token (64 hex chars).
     */
    public String generateToken() {
        byte[] randomBytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(randomBytes);
        return HexFormat.of().formatHex(randomBytes);
    }

    /**
     * Calculates SHA-256 hash of a raw token for secure database storage/lookup.
     */
    public String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    /**
     * Calculates the expiry time for a new invitation (48 hours from now).
     */
    public LocalDateTime calculateExpiryTime() {
        return LocalDateTime.now().plusHours(48);
    }

    /**
     * Checks if a given expiry time has passed.
     */
    public boolean isExpired(LocalDateTime expiryTime) {
        return expiryTime != null && LocalDateTime.now().isAfter(expiryTime);
    }
}
