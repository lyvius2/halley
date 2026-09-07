package banghak.home.halley.domain.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 외부 시세나 금융 API 없이 예산 계획을 계산하는 결정론적 도메인 서비스. */
public final class BudgetCalculator {

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12L);
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100L);

    public BudgetSummary calculate(BudgetPlan plan, BudgetFinancing financing,
                                   List<BudgetAsset> assets, List<BudgetItem> items) {
        if (plan == null || financing == null) {
            throw new IllegalArgumentException("plan and financing are required");
        }
        final List<BudgetAsset> safeAssets = assets == null ? List.of() : List.copyOf(assets);
        final List<BudgetItem> safeItems = items == null ? List.of() : List.copyOf(items);
        final long ownHousingCash = ownHousingCash(plan, financing);
        final long housingAncillary = add(plan.acquisitionTax(), plan.brokerageFee(),
                plan.registrationFee(), plan.otherInitialCost());
        final long householdBudget = safeItems.stream()
                .filter(BudgetItem::includedInBudget)
                .mapToLong(BudgetItem::budgetAmountWon)
                .sum();
        final long movingCost = add(plan.movingCost(), plan.cleaningCost());
        final long totalInitialNeed = add(ownHousingCash, housingAncillary,
                householdBudget, movingCost);
        final long investableAssets = safeAssets.stream()
                .mapToLong(BudgetAsset::effectiveInvestableAmount)
                .sum();
        final long supportFunds = add(plan.parentSupport(), plan.otherFunds());
        final long securedFunds = add(financing.loanAmount(), investableAssets, supportFunds);
        final long additionalFunds = totalInitialNeed - securedFunds;
        final long monthlyPayment = monthlyPayment(financing);
        final long monthlyFixedCost = add(monthlyPayment, plan.monthlyManagementFee(),
                plan.monthlyOtherHousingCost());
        return new BudgetSummary(plan.purchasePrice(), ownHousingCash, housingAncillary,
                householdBudget, movingCost, totalInitialNeed, financing.loanAmount(),
                investableAssets, supportFunds, securedFunds, additionalFunds,
                monthlyPayment, monthlyFixedCost);
    }

    private long ownHousingCash(BudgetPlan plan, BudgetFinancing financing) {
        final long explicitCash = add(plan.contractCash(), plan.balanceCash());
        if (explicitCash > 0L) {
            return explicitCash;
        }
        return Math.max(0L, plan.purchasePrice() - financing.loanAmount());
    }

    private long monthlyPayment(BudgetFinancing financing) {
        final BigDecimal principal = BigDecimal.valueOf(financing.loanAmount());
        final BigDecimal monthlyRate = financing.interestRate()
                .divide(PERCENT, 20, RoundingMode.HALF_UP)
                .divide(MONTHS_PER_YEAR, 20, RoundingMode.HALF_UP);
        if (principal.signum() == 0) {
            return 0L;
        }
        if (financing.repaymentType() == RepaymentType.INTEREST_ONLY) {
            return principal.multiply(monthlyRate).setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
        if (financing.repaymentType() == RepaymentType.PRINCIPAL_EQUAL) {
            return principal.divide(BigDecimal.valueOf(financing.termMonths()), 12, RoundingMode.HALF_UP)
                    .add(principal.multiply(monthlyRate))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
        final BigDecimal factor = BigDecimal.ONE.add(monthlyRate)
                .pow(financing.termMonths());
        return principal.multiply(monthlyRate).multiply(factor)
                .divide(factor.subtract(BigDecimal.ONE), 12, RoundingMode.HALF_UP)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static long add(long... values) {
        long result = 0L;
        for (final long value : values) {
            result = Math.addExact(result, value);
        }
        return result;
    }
}
