package banghak.home.halley.domain.reference;

import banghak.home.halley.domain.property.ReferenceTrade;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/** 한 법정동·한 달의 실거래 원본. */
public record MonthlyTrades(
        String lawdCd,
        YearMonth dealYm,
        CachedDealType dealType,
        List<ReferenceTrade> trades,
        Instant fetchedAt
) {

    public int count() {
        return trades == null ? 0 : trades.size();
    }
}
