package banghak.home.halley.domain.budget;

/** 예산 계획에서 계산된 금액 요약. 모든 금액은 원 단위다. */
public record BudgetSummary(
        long housingPrice,
        long ownHousingCash,
        long housingAncillaryCost,
        long householdBudget,
        long movingAndSettlingCost,
        long totalInitialNeed,
        long loanAmount,
        long investableAssets,
        long supportFunds,
        long securedFunds,
        long additionalFunds,
        long monthlyPrincipalInterest,
        long monthlyFixedHousingCost
) {

    public boolean hasShortfall() {
        return additionalFunds > 0;
    }

    public long remainingFunds() {
        return Math.max(0L, -additionalFunds);
    }
}
