package dev.kalbarczyk.striply.identity.security;

import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

@Component
public class RefreshTokenHasher {

    @SneakyThrows(NoSuchAlgorithmException.class)
    public byte[] hash(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken must not be null");

        return MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));

    }
}
