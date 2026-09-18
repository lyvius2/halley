package banghak.home.halley.domain.forecast;

import java.math.BigDecimal;

public record TradeStat(BigDecimal median, int count) {

    public boolean isEmpty() {
        return count == 0 || median == null;
    }
}
