package banghak.home.halley.domain.budget;

import java.time.Instant;

/** 그룹 구성원 한 명의 자산. 평가액과 실제 투입액을 분리한다. */
public record BudgetAsset(
        Long id,
        Long planId,
        Long userId,
        AssetType assetType,
        String assetName,
        long estimatedValue,
        boolean excluded,
        long investableAmount,
        String sourceType,
        String note,
        Instant createdAt,
        Instant updatedAt
) {

    public BudgetAsset {
        if (planId == null || userId == null) {
            throw new IllegalArgumentException("planId and userId are required");
        }
        if (assetType == null) {
            throw new IllegalArgumentException("assetType is required");
        }
        if (assetName == null || assetName.isBlank()) {
            throw new IllegalArgumentException("assetName is required");
        }
        if (estimatedValue < 0 || investableAmount < 0 || investableAmount > estimatedValue) {
            throw new IllegalArgumentException("asset amounts are invalid");
        }
        sourceType = sourceType == null || sourceType.isBlank() ? "MANUAL" : sourceType;
    }

    public long effectiveInvestableAmount() {
        return excluded ? 0L : investableAmount;
    }
}
