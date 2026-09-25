package banghak.home.halley.domain.loan;

public record CollateralValuation(long value, CollateralSource source, int sampleCount) {

    public static CollateralValuation of(long value, CollateralSource source) {
        return new CollateralValuation(value, source, 0);
    }

    public boolean isReliable() {
        if (source == CollateralSource.KB_PRICE) {
            return true;
        }
        // 거래 두세 건으로 시세를 말할 수는 없다
        return source == CollateralSource.RECENT_TRADE && sampleCount >= MIN_RELIABLE_SAMPLES;
    }

    /** 이 건수 미만이면 실거래 기반 값이라도 신뢰도가 낮다고 본다.  */
    public static final int MIN_RELIABLE_SAMPLES = 3;
}
