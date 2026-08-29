package banghak.home.halley.domain.loan;

/**
 * 담보가치 추정 결과 (설계 I64-1 · I65).
 *
 * @param value       담보가치(원)
 * @param source      어디서 얻은 값인지 — 화면에 함께 표기한다
 * @param sampleCount 실거래로 매겼을 때 쓴 거래 건수. 다른 출처면 0
 */
public record CollateralValuation(long value, CollateralSource source, int sampleCount) {

    public static CollateralValuation of(long value, CollateralSource source) {
        return new CollateralValuation(value, source, 0);
    }

    public boolean isReliable() {
        if (source == CollateralSource.KB_PRICE) {
            return true;
        }
        // 거래 두세 건으로 시세를 말할 수는 없다 (설계 I65)
        return source == CollateralSource.RECENT_TRADE && sampleCount >= MIN_RELIABLE_SAMPLES;
    }

    /** 이 건수 미만이면 실거래 기반 값이라도 신뢰도가 낮다고 본다. */
    public static final int MIN_RELIABLE_SAMPLES = 3;
}
