package banghak.home.halley.domain.forecast;

import java.util.List;

public record FactorTally(int up, int flat, int down) {

    public static FactorTally of(List<PriceFactor> factors) {
        if (factors == null || factors.isEmpty()) {
            return new FactorTally(0, 0, 0);
        }
        int up = 0;
        int flat = 0;
        int down = 0;
        for (final PriceFactor factor : factors) {
            final ForecastDirection effect = factor.effect();
            if (effect == ForecastDirection.UP) {
                up++;
            } else if (effect == ForecastDirection.DOWN) {
                down++;
            } else {
                // null·UNCERTAIN 도 유지로 센다 — 방향을 안 준 것은 다 같다
                flat++;
            }
        }
        return new FactorTally(up, flat, down);
    }

    public int total() {
        return up + flat + down;
    }

    public boolean noSignal() {
        return total() == 0 || flat == total();
    }

    public ForecastDirection direction() {
        if (noSignal()) {
            return ForecastDirection.FLAT;
        }
        // ② 과반수가 유지면 무조건 유지. `flat * 2 > total` 이 "절반 초과"다
        if (flat * 2 > total()) {
            return ForecastDirection.FLAT;
        }
        // ③ 유지와 하락이 동수이고 상승이 그보다 적으면 — 유지 > 하락
        if (flat == down && up < flat) {
            return ForecastDirection.FLAT;
        }
        // ④ 동수면 상승 — 상승 > 하락
        if (up >= down) {
            return ForecastDirection.UP;
        }
        return ForecastDirection.DOWN;
    }
}
