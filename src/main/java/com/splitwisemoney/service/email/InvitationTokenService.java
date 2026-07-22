package com.splitwisemoney.service.email;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class InvitationTokenService {

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a cryptographically secure 36-character invitation token using UUID + SecureRandom.
     */
    public String generateToken() {
        long mostSigBits = secureRandom.nextLong();
        long leastSigBits = secureRandom.nextLong();
        return new UUID(mostSigBits, leastSigBits).toString();
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
