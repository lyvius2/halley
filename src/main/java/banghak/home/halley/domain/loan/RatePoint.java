package banghak.home.halley.domain.loan;

import java.math.BigDecimal;
import java.time.YearMonth;

public record RatePoint(YearMonth month, BigDecimal rate) {
}
