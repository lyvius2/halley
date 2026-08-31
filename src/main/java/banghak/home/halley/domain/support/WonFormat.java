package banghak.home.halley.domain.support;

/**
 * 금액을 억·만원으로 읽기 쉽게 (설계 I130).
 *
 * <p><b>설명 문구 전용입니다.</b> 계산에는 원 단위를 그대로 씁니다 — 여기서 반올림된 값이
 * 계산에 섞이면 안 됩니다.
 *
 * <p>채점 근거(`PriceScorer`)와 전망 근거(`TradeTrendIndicator`)가 같은 표기를 씁니다.
 * 두 화면이 같은 금액을 다르게 쓰면 사용자가 다른 값으로 읽습니다.
 */
public final class WonFormat {

    private WonFormat() {
    }

    public static String of(long amount) {
        final long eok = amount / 100_000_000L;
        final long man = (amount % 100_000_000L) / 10_000L;
        if (eok > 0 && man > 0) {
            return String.format("%d억 %,d만원", eok, man);
        }
        if (eok > 0) {
            return eok + "억원";
        }
        return String.format("%,d만원", man);
    }
}
