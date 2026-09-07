package banghak.home.halley.domain.forecast;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 가격이 어느 쪽으로 움직일 것으로 보는가. */
public enum ForecastDirection {

    UP("상승"),
    DOWN("하락"),
 /** "횡보"보다 "유지". 횡보는 시세 용어라 한 번 더 생각하게 만듭니다. */
    FLAT("유지"),
 /** 판단하지 않았다. '약한 전망'이 아니라 '모른다'입니다. */
    UNCERTAIN("판단 보류");

    private final String label;

    ForecastDirection(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

 /** 지표들이 가리키는 쪽을 세어 많은 쪽. */
    public static ForecastDirection majorityOf(List<PriceFactor> factors) {
        if (factors == null || factors.isEmpty()) {
            return UNCERTAIN;
        }
        final Map<ForecastDirection, Integer> counts = new EnumMap<>(ForecastDirection.class);
        for (final PriceFactor factor : factors) {
            final ForecastDirection effect = factor.effect();
            if (effect == null || effect == UNCERTAIN) {
                continue;
            }
            counts.merge(effect, 1, Integer::sum);
        }
        if (counts.isEmpty()) {
            return UNCERTAIN;
        }
        final int top = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (final ForecastDirection candidate : List.of(UP, FLAT, DOWN)) {
            if (counts.getOrDefault(candidate, 0) == top) {
                return candidate;
            }
        }
        return UNCERTAIN;
    }
}
