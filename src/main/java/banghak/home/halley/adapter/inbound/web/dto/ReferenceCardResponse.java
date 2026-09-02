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
 * @param fetched        국토부에서 <b>받은</b> 거래 수 (설계 I232)
 * @param areaMismatch   이름은 맞는데 <b>전용면적이 하나도 안 맞아</b> 다른 평형을
 *                       대신 보여 주는 중인가 (설계 I232). 이때는 호가 대비 괴리를
 *                       내지 않습니다 — <b>다른 평형과 견준 괴리는 뜻이 없습니다</b>
 * @param nameMatched    그중 <b>단지명이 맞은</b> 수. `fetched` 는 큰데 이게 0이면 이름 문제,
 *                       이게 있는데 결과가 0이면 <b>면적</b> 문제입니다 —
 *                       "없습니다" 한 줄로는 <b>어느 쪽인지 알 수 없었습니다</b>
 */
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

    /**
     * <b>지금 받아 오는 중</b> (설계 I259).
     *
     * <p>화면은 기다리지 않고 배경에서 받아 옵니다([I106]). 그동안 {@code fetched=0}
     * 을 돌려주었더니 화면이 <b>"국토부 신고 자료가 없습니다"</b> 라고 단정했습니다 —
     * <b>안 물어본 것과 물어봤는데 없는 것이 구분되지 않았습니다.</b>
     */
    public static ReferenceCardResponse looking(Long askingPrice, int lookbackMonths, String lawdCd) {
        return new ReferenceCardResponse(List.of(), askingPrice, null, null,
                lookbackMonths, lawdCd, 0, 0, false, true);
    }

    /** 조회를 못 해 본 경우 — 셀 것이 없다. <b>더 기다려도 안 채워집니다</b> */
    public static ReferenceCardResponse notLookedUp(Long askingPrice, int lookbackMonths, String lawdCd) {
        return new ReferenceCardResponse(List.of(), askingPrice, null, null,
                lookbackMonths, lawdCd, 0, 0, false, false);
    }
}
