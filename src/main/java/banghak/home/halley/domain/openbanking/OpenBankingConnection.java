package banghak.home.halley.domain.openbanking;

import java.time.Instant;

/** 사용자의 금융결제원 오픈뱅킹 연결 자격 증명이다. */
public record OpenBankingConnection(Long id, Long userId, OpenBankingProvider provider,
                                    String encryptedAccessToken, String encryptedRefreshToken,
                                    Instant tokenExpiresAt, Instant consentedAt, Instant disconnectedAt,
                                    Instant createdAt, Instant updatedAt) {
    public OpenBankingConnection {
        if (userId == null || provider == null || encryptedAccessToken == null || encryptedAccessToken.isBlank()
                || tokenExpiresAt == null || consentedAt == null) {
            throw new IllegalArgumentException("Open Banking connection is incomplete");
        }
    }

    public boolean isActive() {
        return disconnectedAt == null;
    }
}
