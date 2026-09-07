package banghak.home.halley.domain.budget;

import java.time.Instant;

public record BudgetItemCatalog(
        Long id, String seedKey, String category, String itemName,
        boolean required, boolean recommended, long defaultBudgetAmountWon,
        String candidateName, String candidateUrl, String alternativeName,
        String alternativeUrl, String note, Instant createdAt) {
    public BudgetItemCatalog {
        if (seedKey == null || seedKey.isBlank() || category == null || category.isBlank()
                || itemName == null || itemName.isBlank() || defaultBudgetAmountWon < 0) {
            throw new IllegalArgumentException("catalog fields are invalid");
        }
    }
}
