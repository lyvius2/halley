package banghak.home.halley.domain.forecast;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 가격이 어느 쪽으로 움직일 것으로 보는가 (설계 I130 · I234).
 *
 * <p><b>{@code UNCERTAIN}은 이제 마지막 수단입니다.</b> 한때는 재료가 모자라면
 * 곧바로 "모른다"고 답했는데, 실제로 써 보니 <b>거의 모든 매물이 판단 보류</b>였습니다 —
 * 지표가 <b>여럿 나와 있는데도</b> 그랬습니다. 알아낸 것을 안 보여 주는 셈이었습니다.
 *
 * <p>이제 <b>지표들이 가리키는 쪽을 세어</b> 많은 쪽을 말합니다. 셀 것이 하나도
 * 없을 때만 {@code UNCERTAIN} 입니다.
 */
public enum ForecastDirection {

    UP("상승"),
    DOWN("하락"),
    /** <b>"횡보"보다 "유지"</b> — 횡보는 시세 용어라 한 번 더 생각하게 만듭니다. */
    FLAT("유지"),
    /** 판단하지 않았다. <b>'약한 전망'이 아니라 '모른다'입니다.</b> */
    UNCERTAIN("판단 보류");

    private final String label;

    ForecastDirection(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 지표들이 가리키는 쪽을 세어 많은 쪽 (설계 I234).
     *
     * <p><b>무게를 쓰지 않고 그냥 셉니다.</b> 무게를 곱하면 "실거래 추세 하나가
     * 나머지 셋을 이긴다" 같은 일이 생기는데, 그 비율을 정당화할 근거가 없습니다
     * (규칙 예측이 무게를 쓰는 것은 <b>다른 목적</b>입니다 — 거기서는 표차로
     * 확신도를 잽니다).
     *
     * <p>동수일 때:
     * <ol>
     *   <li><b>상승이 끼어 있으면 상승.</b> 오를 수도 있다는 신호를 묻어 두면
     *       기다리다 놓칩니다 — 이 도구는 살 집을 고르는 자리입니다</li>
     *   <li>상승이 없고 유지·하락이 갈리면 <b>유지.</b> 하락을 단정하려면
     *       그쪽이 더 많아야 합니다</li>
     * </ol>
     *
     * @return 셀 것이 없으면 {@code UNCERTAIN}
     */
    public static ForecastDirection majorityOf(List<PriceFactor> factors) {
        if (factors == null || factors.isEmpty()) {
            return UNCERTAIN;
        }
        final Map<ForecastDirection, Integer> counts = new EnumMap<>(ForecastDirection.class);
        for (final PriceFactor factor : factors) {
            final ForecastDirection effect = factor.effect();
            // 요인이 "모르겠다"고 하는 것은 표가 아니다
            if (effect == null || effect == UNCERTAIN) {
                continue;
            }
            counts.merge(effect, 1, Integer::sum);
        }
        if (counts.isEmpty()) {
            return UNCERTAIN;
        }
        final int top = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        // 동수면 UP → FLAT → DOWN 순으로 고른다
        for (final ForecastDirection candidate : List.of(UP, FLAT, DOWN)) {
            if (counts.getOrDefault(candidate, 0) == top) {
                return candidate;
            }
        }
        return UNCERTAIN;
    }
}
