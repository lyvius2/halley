package banghak.home.halley.domain.finance;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 금감원에 공시된 대출 상품 하나. */
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

 /** 이 상품에서 가장 낮은 대표 금리. 상품끼리 줄 세울 때 쓴다. */
    public Optional<LoanRateOption> cheapestOption() {
        return options.stream()
                .filter(o -> o.representativeRate() != null)
                .min(Comparator.comparing(LoanRateOption::representativeRate));
    }
}
