package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;
import java.util.List;

/** 참고 실거래 카드. */
public record ReferenceCardResponse(
        List<ReferenceTransactionResponse> transactions,
        Long askingPrice,
        BigDecimal gapPercent,
        String dealMonth,
        int lookbackMonths,
        String lawdCd,
        int fetched,
        int nameMatched,
        boolean areaMismatch,
        boolean looking
) {

 /** 지금 받아 오는 중. */
    public static ReferenceCardResponse looking(Long askingPrice, int lookbackMonths, String lawdCd) {
        return new ReferenceCardResponse(List.of(), askingPrice, null, null,
                lookbackMonths, lawdCd, 0, 0, false, true);
    }

 /** 조회를 못 해 본 경우. 셀 것이 없다. 더 기다려도 안 채워집니다 */
    public static ReferenceCardResponse notLookedUp(Long askingPrice, int lookbackMonths, String lawdCd) {
        return new ReferenceCardResponse(List.of(), askingPrice, null, null,
                lookbackMonths, lawdCd, 0, 0, false, false);
    }
}
