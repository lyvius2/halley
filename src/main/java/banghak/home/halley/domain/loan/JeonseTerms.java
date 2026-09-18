package banghak.home.halley.domain.loan;

import java.math.BigDecimal;

public record JeonseTerms(
        BigDecimal guaranteeRate,
        long guaranteeCap,
        BigDecimal interestRate,
        int termYears
) {
}
