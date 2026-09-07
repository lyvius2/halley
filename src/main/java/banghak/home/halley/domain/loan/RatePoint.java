package banghak.home.halley.domain.loan;

import java.math.BigDecimal;
import java.time.YearMonth;

/** 한 시점의 금리. */
public record RatePoint(YearMonth month, BigDecimal rate) {
}
