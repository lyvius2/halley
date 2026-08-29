package banghak.home.halley.domain.loan;

import java.math.BigDecimal;

/**
 * 대출 산정에 쓰는 규제 수치 (설계 3.4 · I64).
 *
 * <p>모든 값은 `regulation_param` 테이블의 활성 프로파일에서 옵니다. 규제가 바뀌면 새 프로파일을
 * 통째로 추가하고 활성 프로파일만 바꿉니다 — 옛 프로파일이 남아야 과거 산출값을 재현할 수 있습니다.
 *
 * @param leaseDeduction     방공제(소액임차보증금 최우선변제금, 원). LTV 한도에서 차감된다.
 *                           주택임대차보호법 시행령 개정 때마다 바뀌므로 반드시 프로파일로 관리한다
 * @param officialPriceRatio 공시가격 현실화율. 공시가격을 담보가치로 환산할 때 나눈다
 */
public record RegulationParams(
        BigDecimal ltvRate,
        long totalCap,
        BigDecimal dsrRatio,
        BigDecimal interestRate,
        BigDecimal stressRate,
        int termYears,
        BigDecimal acquisitionTaxRate,
        BigDecimal firstHomeDiscount,
        long leaseDeduction,
        BigDecimal officialPriceRatio
) {

    public static RegulationParams defaults() {
        return new RegulationParams(
                new BigDecimal("0.4"), 990_000_000L, new BigDecimal("0.4"),
                new BigDecimal("0.04"), new BigDecimal("0.01"), 30,
                new BigDecimal("0.01"), new BigDecimal("0.5"),
                55_000_000L, new BigDecimal("0.7"));
    }
}
