package com.azure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest {

    private static final String LEGACY_HASH = "e10adc3949ba59abbe56e057f20f883e";
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void springCreatesHasherWithoutAdditionalConfiguration() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                PasswordHasher.class)) {
            assertThat(context.getBeansOfType(PasswordHasher.class)).hasSize(1);
            PasswordHasher component = context.getBean(PasswordHasher.class);
            assertThat(context.getBean(PasswordHasher.class)).isSameAs(component);
            String stored = component.encode("New123");
            assertThat(component.matches("New123", stored)).isTrue();
        }
    }

    @Test
    void freshSaltsProduceDistinctHashesThatBothVerifyAndFitTheDatabaseColumn() {
        String first = hasher.encode("New123");
        String second = hasher.encode("New123");

        assertThat(first).startsWith("$argon2id$v=19$m=19456,t=2,p=1$")
                .hasSize(97).hasSizeLessThanOrEqualTo(128).isNotEqualTo(second);
        assertThat(second).hasSizeLessThanOrEqualTo(128);
        assertThat(hasher.matches("New123", first)).isTrue();
        assertThat(hasher.matches("New123", second)).isTrue();
        assertThat(hasher.matches("Wrong123", first)).isFalse();
        assertThat(hasher.needsUpgrade(first)).isFalse();
    }

    @Test
    void passwordEncodingPreservesUnicodeAndWhitespace() {
        String password = " 中文密码🔐 123 ";
        String stored = hasher.encode(password);

        assertThat(hasher.matches(password, stored)).isTrue();
        assertThat(hasher.matches(password.trim(), stored)).isFalse();
    }

    @Test
    void legacyMd5IsVerifiedWithoutTreatingItsStoredHashAsThePassword() {
        assertThat(hasher.matches("123456", LEGACY_HASH)).isTrue();
        assertThat(hasher.matches("654321", LEGACY_HASH)).isFalse();
        assertThat(hasher.matches(LEGACY_HASH, LEGACY_HASH)).isFalse();
        assertThat(hasher.needsUpgrade(LEGACY_HASH)).isTrue();
    }

    @Test
    void validHashWithLowerMemoryCostCanBeVerifiedAndMarkedForUpgrade() {
        PasswordEncoder previousEncoder = new Argon2PasswordEncoder(16, 32, 1, 16 * 1024, 2);
        String stored = previousEncoder.encode("New123");

        assertThat(hasher.matches("New123", stored)).isTrue();
        assertThat(hasher.needsUpgrade(stored)).isTrue();
    }

    @Test
    void nullPasswordNeverAuthenticatesEvenWithAValidStoredHash() {
        assertThat(hasher.matches(null, LEGACY_HASH)).isFalse();
        assertThat(hasher.matches(null, hasher.encode("New123"))).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "", " ", "New123", "{noop}New123", "not-a-password-hash",
            "E10ADC3949BA59ABBE56E057F20F883E", LEGACY_HASH + " ",
            "$argon2i$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA",
            "$argon2id$", "$argon2id$v=19$m=19456,t=2,p=1",
            "$argon2id$v=19$m=19456,t=2,p=1$c2FsdA",
            "$argon2id$v=19$m=not-a-number,t=2,p=1$c2FsdA$aGFzaA",
            "$argon2id$v=19$m=19456,t=2,p=1$!$aGFzaA",
            "$argon2id$v=19$m=19456,t=2,p=1$A$aGFzaA"
    })
    void missingUnknownOrMalformedHashesAreRejected(String storedHash) {
        assertThat(hasher.matches("New123", storedHash)).isFalse();
        assertThat(hasher.needsUpgrade(storedHash)).isFalse();
    }

    @Test
    void extraTrailingFieldsCannotBeIgnoredDuringVerification() {
        String stored = hasher.encode("New123") + "$unexpected";

        assertThat(hasher.matches("New123", stored)).isFalse();
        assertThat(hasher.needsUpgrade(stored)).isFalse();
    }
}
