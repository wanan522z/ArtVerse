package com.artverse.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BCryptPasswordEncoder")
class BCryptPasswordEncoderTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    @Test
    @DisplayName("encode produces valid bcrypt hash")
    void encodeProducesHash() {
        String hash = encoder.encode("password123");

        assertThat(hash).startsWith("$2a$10$");
        assertThat(hash).hasSize(60);
    }

    @Test
    @DisplayName("matches returns true for correct password")
    void matchesCorrect() {
        String hash = encoder.encode("mySecret1!");

        assertThat(encoder.matches("mySecret1!", hash)).isTrue();
    }

    @Test
    @DisplayName("matches returns false for wrong password")
    void matchesWrong() {
        String hash = encoder.encode("mySecret1!");

        assertThat(encoder.matches("wrongPassword", hash)).isFalse();
    }

    @Test
    @DisplayName("matches returns false when raw is null")
    void matchesNullRaw() {
        assertThat(encoder.matches(null, "$2a$10$abc")).isFalse();
    }

    @Test
    @DisplayName("matches returns false when hashed is null")
    void matchesNullHash() {
        assertThat(encoder.matches("password", null)).isFalse();
    }

    @Test
    @DisplayName("same password produces different hashes")
    void producesDifferentSalts() {
        String h1 = encoder.encode("password");
        String h2 = encoder.encode("password");

        assertThat(h1).isNotEqualTo(h2);
        assertThat(encoder.matches("password", h1)).isTrue();
        assertThat(encoder.matches("password", h2)).isTrue();
    }
}
