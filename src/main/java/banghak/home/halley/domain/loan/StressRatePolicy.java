package banghak.home.halley.domain.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 기준 스트레스 금리 산식. */
public final class StressRatePolicy {

    private StressRatePolicy() {
    }

 /** 금리 시계열에서 스트레스 금리를 산출한다. */
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

 /** 스트레스 금리 산출 결과와 근거다. */
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
