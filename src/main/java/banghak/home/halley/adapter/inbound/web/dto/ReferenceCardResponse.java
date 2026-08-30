package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 참고 실거래 카드 (설계 I98).
 *
 * @param lookbackMonths 몇 개월을 훑었는지. <b>비었을 때 화면이 그 사실을 말할 수 있어야</b>
 *                       합니다 — "없습니다"만 뜨면 얼마나 찾아본 것인지 알 수 없습니다
 */
public record ReferenceCardResponse(
        List<ReferenceTransactionResponse> transactions,
        Long askingPrice,
        BigDecimal gapPercent,
        String dealMonth,
        int lookbackMonths
) {
}
