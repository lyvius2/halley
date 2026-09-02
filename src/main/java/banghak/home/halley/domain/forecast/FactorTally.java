package banghak.home.halley.domain.forecast;

import java.util.List;

/**
 * 지표를 세어 방향을 정한다 (설계 I248).
 *
 * <p><b>결론은 지표에서 나옵니다.</b> LLM 이 스스로 낸 방향은 쓰지 않습니다 —
 * LLM 은 <b>지표와 근거만</b> 제공하고 판정은 우리가 합니다.
 *
 * <h4>유지 = 판단 보류</h4>
 *
 * <p>둘을 같은 것으로 봅니다. `유지`는 <b>"올라가지도 내려가지도 않는다"고 단언하는
 * 말</b>이 아니라 <b>"방향을 말하지 않겠다"</b>는 뜻입니다. 실제로 파서가
 * LLM 의 "모르겠다"를 `FLAT` 으로 바꿔 넣고 있어([I247]), `FLAT` 은 이미
 * <b>모름의 하치장</b>입니다.
 *
 * <h4>규칙</h4>
 *
 * <pre>
 * n = 지표 수 · u = 상승 · f = 유지 · d = 하락
 *
 * ① n == 0 또는 f == n     →  유지   (신호 자체가 없다)
 * ② f > n/2                →  유지   (과반수가 유지면 무조건)
 * ③ f == d 이고 u < f      →  유지   (유지 > 하락)
 * ④ u >= d                 →  상승   (동수면 상승 — 상승 > 하락)
 * ⑤ d > u                  →  하락
 * </pre>
 *
 * <p>우선순위는 <b>상승 &gt; 유지 &gt; 하락</b> 입니다. 같은 수면 위쪽이 이깁니다 —
 * ③과 ④가 그것입니다.
 *
 * <p><b>적극적으로 읽습니다.</b> 유지가 과반이 아니면 방향을 말합니다.
 * 지표 넷 중 상승 둘·하락 둘이면 <b>상승</b>입니다.
 */
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

    /**
     * 셀 것이 없다 (설계 I248).
     *
     * <p>지표가 하나도 없거나 <b>전부 유지</b>인 경우입니다. 방향으로는 둘 다 `유지`
     * 지만, 화면은 <b>다르게 보여 줍니다</b>(🤔) — "재료를 보고 판단을 안 한 것"과
     * "볼 재료가 있는데 갈린 것"은 사람에게 다른 이야기입니다.
     */
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
