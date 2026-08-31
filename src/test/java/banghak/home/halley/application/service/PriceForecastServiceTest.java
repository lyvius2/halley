package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.LlmPort;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.indicator.ForecastInput;
import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmResult;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.property.SourceType;
import banghak.home.halley.domain.reference.CachedDealType;
import banghak.home.halley.domain.reference.MonthlyTrades;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("가격 전망 서비스 (설계 I134)")
class PriceForecastServiceTest {

    @MockitoBean
    private LlmPort llmPort;

    @Autowired
    private PriceForecastService service;

    @Test
    @DisplayName("코드 예측을 LLM에 넘기지 않는다 — 넘기면 두 예측이 독립이 아니게 된다")
    void doesNotLeakCodePredictionToLlm() {
        final AtomicReference<String> sent = new AtomicReference<>();
        stub(sent, """
                {"direction":"DOWN","confidence":"MEDIUM","factors":[],"summary":"","caveats":[]}""");

        service.forecast(input(1_210_000_000L, 1_140_000_000L));

        assertThat(sent.get()).contains("계산된 지표");
        assertThat(sent.get()).doesNotContain("UNCERTAIN").doesNotContain("규칙");
    }

    @Test
    @DisplayName("LLM 판단이 결론이 된다 — 코드와 갈려도")
    void llmVerdictWins() {
        stub(new AtomicReference<>(), """
                {"direction":"UP","confidence":"LOW",
                 "factors":[{"name":"실거래 추세","effect":"UP","weight":"HIGH",
                   "evidence":"최근 3개월 중앙값은 11억 4,000만원입니다"}],
                 "summary":"","caveats":[]}""");

        final var verdict = service.forecast(input(1_210_000_000L, 1_140_000_000L));

        // 코드는 하락으로 봤다
        assertThat(verdict.byCode().direction()).isEqualTo(ForecastDirection.DOWN);
        assertThat(verdict.conclusion().direction()).isEqualTo(ForecastDirection.UP);
        assertThat(verdict.agreed()).isFalse();
    }

    @Test
    @DisplayName("LLM이 죽어도 지표는 그대로 나온다 — 코드 예측으로 되돌아간다")
    void fallsBackWhenLlmFails() {
        when(llmPort.isEnabled()).thenReturn(true);
        when(llmPort.provider()).thenReturn("test");
        when(llmPort.complete(any())).thenReturn(LlmResult.failed("timeout"));

        final var verdict = service.forecast(input(1_210_000_000L, 1_140_000_000L));

        assertThat(verdict.conclusion().direction()).isEqualTo(ForecastDirection.DOWN);
        assertThat(verdict.conclusion().factors()).isNotEmpty();
        assertThat(verdict.agreed()).isTrue();
    }

    @Test
    @DisplayName("지어낸 숫자만 인용했으면 그 답을 믿지 않는다")
    void fallsBackWhenAllFactorsDropped() {
        stub(new AtomicReference<>(), """
                {"direction":"UP","confidence":"HIGH",
                 "factors":[{"name":"인근 개발","effect":"UP","weight":"HIGH",
                   "evidence":"2029년 준공 예정 7만평 단지가 있습니다"}],
                 "summary":"","caveats":[]}""");

        final var verdict = service.forecast(input(1_210_000_000L, 1_140_000_000L));

        // 요인이 전부 걸러졌다 — 코드 예측으로 되돌아간다
        assertThat(verdict.conclusion().direction()).isEqualTo(ForecastDirection.DOWN);
    }

    @Test
    @DisplayName("실거래 표본이 3건 미만이면 LLM이 뭐라 하든 UNCERTAIN — 사실의 문제다")
    void forcesUncertainWhenSamplesTooFew() {
        stub(new AtomicReference<>(), """
                {"direction":"UP","confidence":"HIGH",
                 "factors":[{"name":"금리 국면","effect":"UP","weight":"MEDIUM",
                   "evidence":"금리가 내리는 중입니다"}],
                 "summary":"","caveats":[]}""");

        // 달마다 1건뿐 — 실거래 추세 지표가 안 나온다
        final var verdict = service.forecast(thinInput());

        assertThat(verdict.conclusion().direction()).isEqualTo(ForecastDirection.UNCERTAIN);
        assertThat(verdict.conclusion().caveats()).anyMatch(c -> c.contains("표본이 3건 미만"));
    }

    @Test
    @DisplayName("지표가 하나도 없으면 묻지 않는다 — 재료 없이 물으면 일반론이 온다")
    void doesNotAskWithoutIndicators() {
        final AtomicReference<String> sent = new AtomicReference<>();
        stub(sent, """
                {"direction":"UP","confidence":"HIGH","factors":[],"summary":"","caveats":[]}""");

        final var verdict = service.forecast(
                new ForecastInput(property(), List.of(), List.of(), List.of(), List.of(), null));

        assertThat(sent.get()).isNull();
        assertThat(verdict.conclusion().direction()).isEqualTo(ForecastDirection.UNCERTAIN);
        assertThat(verdict.prompt()).isNull();
    }

    @Test
    @DisplayName("LLM이 꺼져 있으면 코드 예측이 결론이다")
    void usesCodePredictionWhenLlmDisabled() {
        when(llmPort.isEnabled()).thenReturn(false);
        when(llmPort.provider()).thenReturn("test");

        final var verdict = service.forecast(input(1_210_000_000L, 1_140_000_000L));

        assertThat(verdict.conclusion().direction()).isEqualTo(ForecastDirection.DOWN);
        assertThat(verdict.agreed()).isTrue();
    }

    // ── 도우미 ─────────────────────────────────────────────

    private void stub(AtomicReference<String> sent, String answer) {
        when(llmPort.isEnabled()).thenReturn(true);
        when(llmPort.provider()).thenReturn("test");
        when(llmPort.complete(any())).thenAnswer(inv -> {
            sent.set(((LlmMessage) inv.getArgument(0)).user());
            return LlmResult.of(answer, "test-model");
        });
    }

    /** 직전 3개월 / 최근 3개월 가격을 정해 7개월치를 만든다(신고 지연 1달 포함). */
    private ForecastInput input(long oldPrice, long recentPrice) {
        final List<MonthlyTrades> months = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            months.add(month(YearMonth.now().minusMonths(6L - i), i < 3 ? oldPrice : recentPrice, 3));
        }
        return ForecastInput.ofTrades(property(), months);
    }

    /**
     * 실거래는 달마다 1건뿐이라 추세 지표가 안 나오고(3건 미만), 금리만 나온다.
     * 그래야 LLM을 부르고 안전장치가 도는지 볼 수 있다.
     */
    private ForecastInput thinInput() {
        final List<MonthlyTrades> months = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            // 최근 3개월 구간(인덱스 3·4·5)에 1+1+0 = 2건만 → 3건 미만이라 추세 지표가 안 나온다
            final int count = switch (i) {
                case 3, 4 -> 1;
                case 5 -> 0;
                default -> 3;
            };
            months.add(month(YearMonth.now().minusMonths(6L - i), 1_000_000_000L, count));
        }
        final List<banghak.home.halley.domain.loan.RatePoint> rates = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            rates.add(new banghak.home.halley.domain.loan.RatePoint(
                    YearMonth.now().minusMonths(11L - i),
                    new BigDecimal("0.055").subtract(new BigDecimal("0.002")
                            .multiply(BigDecimal.valueOf(i)))));
        }
        return new ForecastInput(property(), months, List.of(), rates, List.of(), null);
    }

    private MonthlyTrades month(YearMonth ym, long price, int count) {
        final List<ReferenceTrade> trades = new ArrayList<>();
        for (int j = 0; j < count; j++) {
            trades.add(new ReferenceTrade("측정단지", price, new BigDecimal("84.9"), 5, LocalDate.now()));
        }
        return new MonthlyTrades("11110", ym, CachedDealType.TRADE, trades, Instant.now());
    }

    private Property property() {
        return new Property(
                1L, "측정단지", null, DealType.SALE, 1_140_000_000L, null,
                null, "서울 강남구 대치동 316", new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, new BigDecimal("84.9"), null, 5, 15, null, null, null,
                2018, null, null, null, 300, null, null, null,
                null, null, null, null, null,
                null, null, null,
                null, null, null,
                SourceType.MANUAL, null, null, null, null, null,
                false, ListingStatus.ACTIVE, true, null, 0, null, null, null, 1L, Instant.now());
    }
}
