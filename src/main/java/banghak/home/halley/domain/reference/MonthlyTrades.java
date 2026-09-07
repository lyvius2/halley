package banghak.home.halley.domain.reference;

import banghak.home.halley.domain.property.ReferenceTrade;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/**
 * 한 법정동·한 달의 실거래 원본.
 *
 * `reference_transaction`과 다릅니다. 그쪽은 매물별로 단지·면적이 맞는 것만
 * 50건까지 걸러 담습니다. 여기는 법정동·월별로 국토부가 준 것을 그대로 담습니다 —
 * 같은 법정동의 다른 매물이 그대로 다시 쓸 수 있어야 하기 때문입니다.
 *
 * 가격 전망은 60개월을 훑는데, 매물마다 60번씩 부르면 등록이 몇 분씩 걸립니다.
 *
 * @param dealType  매매인지 전세인지. 키에 들어갑니다 — 없으면 둘이 서로를 덮어씁니다
 * @param fetchedAt 언제 받았는지. 과거 달은 바뀌지 않으므로 최근 몇 달만 다시 받는다
 */
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
