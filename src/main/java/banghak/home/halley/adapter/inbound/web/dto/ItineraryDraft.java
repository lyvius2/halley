package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

/**
 * 임장 플래너에서 작업 중인 것 (설계 I179).
 *
 * <p><b>계정마다 다릅니다.</b> A가 짜던 동선이 B에게 보이면 안 됩니다 —
 * 같은 그룹이라도 임장은 각자 짭니다.
 *
 * <p>계획으로 <b>저장되기 전</b>의 상태라 DB에 둘 성격이 아닙니다. 다만 새로고침하면
 * 사라지던 것을 남겨 둡니다 — 매물을 고르고 주소를 찾는 데 시간이 걸립니다.
 *
 * @param propertyIds 고른 매물
 * @param travelMode  `TRANSIT` · `DRIVING`
 * @param result      계산 결과. 아직 안 눌렀으면 null
 */
public record ItineraryDraft(
        List<Long> propertyIds,
        String travelMode,
        OptimizeItineraryResponse result
) {

    public static ItineraryDraft empty() {
        return new ItineraryDraft(List.of(), null, null);
    }
}
