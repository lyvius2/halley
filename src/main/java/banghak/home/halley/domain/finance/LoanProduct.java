package banghak.home.halley.domain.finance;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 금감원에 공시된 대출 상품 하나 (설계 I77).
 *
 * @param dclsMonth        공시 제출월 `YYYYMM`. 금리는 월 단위로 갱신되므로 언제 값인지가 중요하다
 * @param loanLimit        대출한도 — 금액이 아니라 "LTV 70% 이내" 같은 <b>서술 문장</b>으로 온다
 * @param incidentalExpense 대출 부대비용, {@code earlyRepayFee} 중도상환 수수료, {@code delayRate} 연체 이자율.
 *                         모두 서술 문장이라 계산에 바로 쓸 수 없고 사람이 읽는 용도다
 */
public record LoanProduct(
        LoanProductType type,
        FinanceGroup group,
        String dclsMonth,
        String finCoNo,
        String companyName,
        String productCode,
        String productName,
        String joinWay,
        String incidentalExpense,
        String earlyRepayFee,
        String delayRate,
        String loanLimit,
        List<LoanRateOption> options
) {

    public LoanProduct {
        options = options == null ? List.of() : List.copyOf(options);
    }

    /** 이 상품에서 가장 낮은 대표 금리 — 상품끼리 줄 세울 때 쓴다. */
    public Optional<LoanRateOption> cheapestOption() {
        return options.stream()
                .filter(o -> o.representativeRate() != null)
                .min(Comparator.comparing(LoanRateOption::representativeRate));
    }
}
