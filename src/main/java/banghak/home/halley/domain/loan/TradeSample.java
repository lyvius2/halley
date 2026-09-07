package banghak.home.halley.domain.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 담보가치 추정에 쓰는 실거래 한 건. */
public record TradeSample(long price, BigDecimal areaM2, LocalDate contractDate) {
}
