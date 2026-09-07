package banghak.home.halley.domain.budget;

import java.time.Instant;

/** 계획에 복사된 혼수 품목. 후보와 대안 상품 정보를 함께 보존한다. */
public record BudgetItem(
        Long id,
        Long planId,
        String seedKey,
        String category,
        String itemName,
        boolean required,
        boolean recommended,
        boolean selected,
        boolean owned,
        long budgetAmountWon,
        String candidateName,
        String candidateUrl,
        Long candidatePriceWon,
        String alternativeName,
        String alternativeUrl,
        Long alternativePriceWon,
        ProductFetchStatus candidateFetchStatus,
        ProductFetchStatus alternativeFetchStatus,
        String note,
        Instant createdAt,
        Instant updatedAt
) {

    public BudgetItem {
        if (planId == null || category == null || category.isBlank()
                || itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("planId, category and itemName are required");
        }
        if (budgetAmountWon < 0 || candidatePriceWon != null && candidatePriceWon < 0
                || alternativePriceWon != null && alternativePriceWon < 0) {
            throw new IllegalArgumentException("item amounts are invalid");
        }
        candidateFetchStatus = candidateFetchStatus == null
                ? ProductFetchStatus.NOT_FETCHED : candidateFetchStatus;
        alternativeFetchStatus = alternativeFetchStatus == null
                ? ProductFetchStatus.NOT_FETCHED : alternativeFetchStatus;
    }

    public boolean includedInBudget() {
        return selected && !owned;
    }
}
