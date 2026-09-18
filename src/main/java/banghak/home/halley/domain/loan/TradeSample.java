package banghak.home.halley.domain.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeSample(long price, BigDecimal areaM2, LocalDate contractDate) {
}
