package banghak.home.halley.domain.forecast;

import java.util.List;

/** 지표를 세어 방향을 정한다. */
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
                flat++;
            }
        }
        return new FactorTally(up, flat, down);
    }

    public int total() {
        return up + flat + down;
    }

 /** 셀 것이 없다. */
    public boolean noSignal() {
        return total() == 0 || flat == total();
    }

    public ForecastDirection direction() {
        if (noSignal()) {
            return ForecastDirection.FLAT;
        }
        if (flat * 2 > total()) {
            return ForecastDirection.FLAT;
        }
        if (flat == down && up < flat) {
            return ForecastDirection.FLAT;
        }
        if (up >= down) {
            return ForecastDirection.UP;
        }
        return ForecastDirection.DOWN;
    }
}
