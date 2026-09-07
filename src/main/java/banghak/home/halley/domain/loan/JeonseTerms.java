package banghak.home.halley.domain.loan;

import java.math.BigDecimal;

/** 전세자금대출 조건. */
public record JeonseTerms(
        BigDecimal guaranteeRate,
        long guaranteeCap,
        BigDecimal interestRate,
        int termYears
) {
}
