package banghak.home.halley.domain.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** PoC 부대비용 참고값을 계산하는 결정론적 규칙 모음이다. */
public final class BudgetCostEstimator {
    private static final long SIX_HUNDRED_MILLION = 600_000_000L;
    private static final long NINE_HUNDRED_MILLION = 900_000_000L;

    public BudgetCostEstimate estimate(BudgetPlan plan, BudgetFinancing financing) {
        final long price = plan.purchasePrice();
        final long area = roundedArea(plan.exclusiveAreaM2());
        final boolean sale = plan.housingType() == HousingType.SALE;
        final long acquisitionTax = sale ? percentage(price, acquisitionTaxRate(price)) : 0L;
        final long brokerageFee = sale ? brokerageFee(price) : 0L;
        final long registrationFee = sale ? 500_000L + percentage(price, new BigDecimal("0.15"))
                + percentage(financing.loanAmount(), new BigDecimal("0.05")) : 0L;
        final long movingCost = area == 0 ? 0L : 200_000L + area * 12_000L;
        final long cleaningCost = area == 0 ? 0L : 100_000L + area * 12_000L;
        return new BudgetCostEstimate(acquisitionTax, brokerageFee, registrationFee, movingCost, cleaningCost,
                List.of("취득세: 매매가 구간별 PoC 참고 세율", "중개보수: 서울 매매 상한요율·한도 참고",
                        "법무사·등기: 기본 50만원 + 매매가 0.15% + 대출금 0.05% 참고",
                        "이사·입주청소: 전용면적 ㎡당 각 1만 2천원과 기본비 참고"),
                List.of("확정 세액·수수료·견적이 아닙니다. 주택 수·지역·거래 조건 및 실제 견적을 확인하세요.",
                        "면적이 없으면 이사·입주청소 비용은 추정하지 않습니다."));
    }

    private BigDecimal acquisitionTaxRate(long price) {
        if (price <= SIX_HUNDRED_MILLION) return new BigDecimal("1.0");
        if (price <= NINE_HUNDRED_MILLION) return new BigDecimal("2.0");
        return new BigDecimal("3.0");
    }

    private long brokerageFee(long price) {
        if (price < 50_000_000L) return Math.min(percentage(price, new BigDecimal("0.6")), 250_000L);
        if (price < 200_000_000L) return Math.min(percentage(price, new BigDecimal("0.5")), 800_000L);
        if (price < NINE_HUNDRED_MILLION) return percentage(price, new BigDecimal("0.4"));
        return percentage(price, new BigDecimal("0.5"));
    }

    private long percentage(long amount, BigDecimal rate) {
        return BigDecimal.valueOf(amount).multiply(rate).divide(new BigDecimal("100")).longValue();
    }

    private long roundedArea(BigDecimal area) {
        return area == null || area.signum() <= 0 ? 0L : area.setScale(0, RoundingMode.UP).longValue();
    }
}
