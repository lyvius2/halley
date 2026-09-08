package banghak.home.halley.domain.openbanking;

import java.time.Instant;

/** 연결된 계좌의 식별 정보와 마지막 조회 잔액이다. */
public record OpenBankingAccount(Long id, Long connectionId, String fintechUseNumHash,
                                 String encryptedFintechUseNum, String fintechUseNumMasked,
                                 String bankCode, String bankName, String productName, String accountType,
                                 Long lastBalanceAmount, Long lastAvailableAmount, Instant lastSyncedAt,
                                 Long linkedBudgetAssetId, boolean active, Instant createdAt, Instant updatedAt) {
    public OpenBankingAccount {
        if (connectionId == null || fintechUseNumHash == null || fintechUseNumHash.isBlank()
                || encryptedFintechUseNum == null || encryptedFintechUseNum.isBlank()
                || fintechUseNumMasked == null || fintechUseNumMasked.isBlank()) {
            throw new IllegalArgumentException("Open Banking account is incomplete");
        }
        if ((lastBalanceAmount != null && lastBalanceAmount < 0)
                || (lastAvailableAmount != null && lastAvailableAmount < 0)) {
            throw new IllegalArgumentException("Account balance must not be negative");
        }
    }
}
