package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 참고 실거래 카드 (설계 I98).
 *
 * @param lookbackMonths 몇 개월을 훑었는지. <b>비었을 때 화면이 그 사실을 말할 수 있어야</b>
 *                       합니다 — "없습니다"만 뜨면 얼마나 찾아본 것인지 알 수 없습니다
 * @param lawdCd         <b>실제로 무엇으로 물었는지</b> (설계 I227). 화면은 빈 칸과
 *                       엉뚱한 예시(`11010`)만 보여 줘서, 코드가 맞는지 <b>확인할 방법이
 *                       없었습니다.</b> 못 구했으면 null — 그 자체가 비어 있는 이유입니다
 */
public record ReferenceCardResponse(
        List<ReferenceTransactionResponse> transactions,
        Long askingPrice,
        BigDecimal gapPercent,
        String dealMonth,
        int lookbackMonths,
        String lawdCd
) {
}
