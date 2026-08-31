package banghak.home.halley.domain.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 기준 스트레스 금리 산식 (설계 I116).
 *
 * <pre>
 *   기준 스트레스 금리 = clamp(과거 5년 최고 가계대출금리 − 현재 금리, 1.5%, 3.0%)
 * </pre>
 *
 * <p>순수 계산이라 도메인에 둡니다. 외부 호출도 DB도 없어 값만 넣으면 검증됩니다.
 *
 * <p><b>하한·상한을 파라미터로 받습니다.</b> 규제가 바뀌면 숫자만 갈아 끼웁니다 —
 * 1.5%·3.0%를 코드에 박아 두면 고시가 바뀔 때 배포해야 합니다.
 */
public final class StressRatePolicy {

    private StressRatePolicy() {
    }

    /**
     * @param series 월별 가계대출 금리(소수). 비어 있으면 계산하지 않는다
     * @return 산출된 기준 스트레스 금리. 자료가 모자라면 {@code empty} —
     *         <b>이때는 사람이 넣어 둔 값을 그대로 둡니다.</b> 못 받았다고 0을 쓰면
     *         스트레스가 사라져 한도가 실제보다 넉넉하게 나옵니다
     */
    public static Optional<StressRateDecision> decide(List<RatePoint> series,
                                                      BigDecimal floor,
                                                      BigDecimal cap) {
        if (series == null || series.isEmpty()) {
            return Optional.empty();
        }
        final Optional<RatePoint> peak = series.stream().max(Comparator.comparing(RatePoint::rate));
        final Optional<RatePoint> latest = series.stream().max(Comparator.comparing(RatePoint::month));
        if (peak.isEmpty() || latest.isEmpty()) {
            return Optional.empty();
        }
        final BigDecimal gap = peak.get().rate().subtract(latest.get().rate());
        final BigDecimal clamped = gap.max(floor).min(cap).setScale(6, RoundingMode.HALF_UP);
        return Optional.of(new StressRateDecision(
                clamped, peak.get().rate(), peak.get().month(),
                latest.get().rate(), latest.get().month(), series.size()));
    }

    /**
     * 산출 결과와 <b>그 근거</b>. 화면과 로그가 "왜 이 값인가"를 말할 수 있어야 합니다 —
     * 근거 없는 금리는 검증할 수 없습니다 (설계 I81과 같은 이유).
     */
    public record StressRateDecision(
            BigDecimal stressRate,
            BigDecimal peakRate,
            YearMonth peakMonth,
            BigDecimal currentRate,
            YearMonth currentMonth,
            int samples) {

        /** 화면에 그대로 띄우는 한 줄. */
        public String source() {
            return String.format("한국은행 ECOS 기준 — 최고 %s%% (%s) − 현재 %s%% (%s), %d개월치",
                    percent(peakRate), peakMonth, percent(currentRate), currentMonth, samples);
        }

        private static String percent(BigDecimal rate) {
            return rate.multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
