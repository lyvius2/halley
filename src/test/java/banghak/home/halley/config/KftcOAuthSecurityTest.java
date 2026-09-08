package banghak.home.halley.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("금융결제원 OAuth 보안 기반")
class KftcOAuthSecurityTest {

    @Test
    @DisplayName("토큰은 평문을 노출하지 않는 암호문으로 저장하고 복호화할 수 있다")
    void encryptsAndDecryptsToken() {
        // given
        final KftcOpenBankingProperties properties = propertiesWithEncryptionKey();
        final KftcTokenCipher cipher = new KftcTokenCipher(properties, new SecureRandom());

        // when
        final String encrypted = cipher.encrypt("access-token");

        // then
        assertThat(encrypted).doesNotContain("access-token");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("access-token");
    }

    @Test
    @DisplayName("32바이트가 아닌 암호화 키는 토큰 처리 전에 거부한다")
    void rejectsInvalidEncryptionKey() {
        // given
        final KftcOpenBankingProperties properties = new KftcOpenBankingProperties();
        properties.setTokenEncryptionKey("aW52YWxpZA==");
        final KftcTokenCipher cipher = new KftcTokenCipher(properties, new SecureRandom());

        // when
        // then
        final KftcTokenEncryptionConfigurationException exception = assertThrows(
                KftcTokenEncryptionConfigurationException.class,
                () -> cipher.encrypt("access-token"));
        assertThat(exception).isNotNull();
    }

    @Test
    @DisplayName("OAuth state는 한 번만 검증할 수 있다")
    void consumesStateOnlyOnce() {
        // given
        final KftcOAuthStateStore store = stateStore(Instant.parse("2026-09-08T00:00:00Z"));
        final MockHttpSession session = new MockHttpSession();
        final String state = store.issue(session);

        // when
        final boolean firstAttempt = store.consume(session, state);
        final boolean secondAttempt = store.consume(session, state);

        // then
        assertThat(firstAttempt).isTrue();
        assertThat(secondAttempt).isFalse();
    }

    @Test
    @DisplayName("만료된 OAuth state는 검증에 실패한다")
    void rejectsExpiredState() {
        // given
        final Instant issuedAt = Instant.parse("2026-09-08T00:00:00Z");
        final KftcOAuthStateStore issuer = stateStore(issuedAt);
        final MockHttpSession session = new MockHttpSession();
        final String state = issuer.issue(session);
        final KftcOAuthStateStore validator = stateStore(issuedAt.plus(Duration.ofMinutes(10)));

        // when
        final boolean accepted = validator.consume(session, state);

        // then
        assertThat(accepted).isFalse();
    }

    private static KftcOpenBankingProperties propertiesWithEncryptionKey() {
        final KftcOpenBankingProperties properties = new KftcOpenBankingProperties();
        properties.setTokenEncryptionKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        return properties;
    }

    private static KftcOAuthStateStore stateStore(Instant instant) {
        return new KftcOAuthStateStore(Duration.ofMinutes(10), Clock.fixed(instant, ZoneOffset.UTC),
                new SecureRandom());
    }
}
