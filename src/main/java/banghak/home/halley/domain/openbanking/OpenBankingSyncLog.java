package banghak.home.halley.domain.openbanking;

import java.time.Instant;

/** 계좌 잔액 동기화 요청의 최소 감사 이력이다. */
public record OpenBankingSyncLog(Long id, Long connectionId, Instant requestedAt, Instant completedAt,
                                 OpenBankingSyncStatus status, String failureCode, int accountCount,
                                 Instant createdAt) {
    public OpenBankingSyncLog {
        if (connectionId == null || requestedAt == null || status == null || accountCount < 0) {
            throw new IllegalArgumentException("Open Banking sync log is invalid");
        }
    }
}
