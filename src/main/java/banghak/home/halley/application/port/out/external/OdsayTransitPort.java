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
}
