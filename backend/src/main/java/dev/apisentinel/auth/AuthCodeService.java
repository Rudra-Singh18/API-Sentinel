package dev.apisentinel.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class AuthCodeService {
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESEND_DELAY = Duration.ofMinutes(1);
    private static final int MAX_ATTEMPTS = 5;
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();

    public synchronized boolean isCoolingDown(String email) {
        CodeEntry entry = codes.get(email);
        if (entry == null) return false;
        if (entry.createdAt().plus(CODE_LIFETIME).isBefore(Instant.now())) {
            codes.remove(email);
            return false;
        }
        return entry.createdAt().plus(RESEND_DELAY).isAfter(Instant.now());
    }

    public synchronized void store(String email, String intent, String code) {
        codes.put(email, new CodeEntry(intent, digest(code), Instant.now(), 0));
    }

    public synchronized boolean verify(String email, String intent, String code) {
        CodeEntry entry = codes.get(email);
        if (entry == null) return false;
        if (entry.createdAt().plus(CODE_LIFETIME).isBefore(Instant.now())) {
            codes.remove(email);
            return false;
        }
        boolean matches = entry.intent().equals(intent) && MessageDigest.isEqual(entry.codeHash(), digest(code));
        if (!matches) {
            if (entry.attempts() + 1 >= MAX_ATTEMPTS) codes.remove(email);
            else codes.put(email, new CodeEntry(entry.intent(), entry.codeHash(), entry.createdAt(), entry.attempts() + 1));
            return false;
        }
        codes.remove(email);
        return true;
    }

    private byte[] digest(String code) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record CodeEntry(String intent, byte[] codeHash, Instant createdAt, int attempts) {}
}