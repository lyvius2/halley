package banghak.home.halley.domain.forecast;

import java.math.BigDecimal;

/** 한 구간의 실거래 통계. */
public record TradeStat(BigDecimal median, int count) {

    public boolean isEmpty() {
        return count == 0 || median == null;
    }
}
