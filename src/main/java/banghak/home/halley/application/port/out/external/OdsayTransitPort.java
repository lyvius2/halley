package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.itinerary.RoutePath;
import banghak.home.halley.domain.scoring.TransitResult;

public interface OdsayTransitPort {

    /**
     * 설정이 갖춰져 실제로 호출할 수 있는지 (설계 I119).
     *
     * <p>키가 없으면 조회가 조용히 빈 결과를 내는데, 부르는 쪽이 그것을 <b>경로가 없는 것</b>과
     * 구분하지 못했습니다. 직주근접이 왜 미산출인지 말해 주려면 이 구분이 필요합니다.
     *
     * <p>기본값을 둔 이유는 이 인터페이스를 <b>람다로 대신하는 테스트</b>가 여럿이기 때문입니다.
     * 실제 어댑터는 키를 보고 답합니다.
     */
    default boolean isEnabled() {
        return true;
    }

    TransitResult findTransit(double startX, double startY, double endX, double endY);

    /**
     * 그 경로의 <b>실제 선</b>을 받아 온다 (설계 I177).
     *
     * <p>{@code mapObj} 는 `findTransit` 이 돌려준 열쇠입니다 — 그것 없이는 못 받습니다.
     * 그래서 <b>호출이 한 번 더</b> 듭니다. 지도에 그릴 때만 부릅니다.
     *
     * <p>기본값은 빈 경로입니다 — 이 인터페이스를 <b>람다로 대신하는 테스트</b>가 여럿이라
     * 메서드를 늘리면 그 전부가 깨집니다.
     */
    default RoutePath findLane(String mapObj) {
        return RoutePath.empty();
    }

    /**
     * 여러 구간을 한꺼번에 (설계 I210).
     *
     * <p>임장 행렬은 매물 8개면 <b>72쌍</b>입니다. ODsay 라면 쌍마다 불러도 괜찮지만
     * (50ms), 할당량이 끝나 LLM 으로 넘어가면 쌍마다 부르는 것은 <b>못 씁니다</b> —
     * 한 번 계산에 수십 분입니다.
     *
     * <p>기본 구현은 그냥 돕니다. 묶어서 이득을 보는 구현만 이것을 덮습니다.
     *
     * @param legs 열쇠 → {@code {startX, startY, endX, endY}}
     * @return 답을 낸 것만. <b>못 낸 열쇠는 빠집니다</b> — 빈 자리를 0이나 999로 채우지 않습니다
     */
    default java.util.Map<String, TransitResult> findTransitBatch(java.util.Map<String, double[]> legs) {
        final java.util.Map<String, TransitResult> found = new java.util.LinkedHashMap<>();
        legs.forEach((key, c) -> found.put(key, findTransit(c[0], c[1], c[2], c[3])));
        return found;
    }
}
