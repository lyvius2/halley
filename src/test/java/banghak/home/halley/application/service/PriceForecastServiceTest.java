package banghak.home.halley.application.service;

import banghak.home.halley.domain.forecast.PriceOutlook;
import banghak.home.halley.domain.forecast.ForecastPrompt;
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

    @Autowired
    private banghak.home.halley.adapter.outbound.persistence.PriceForecastRepository forecastRepository;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private banghak.home.halley.adapter.outbound.persistence.UserGroupRepository userGroupRepository;

    @Autowired
    private banghak.home.halley.adapter.outbound.persistence.UserRepository userRepository;

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
        // 답을 못 받았다 (설계 I145). 이걸 표시하지 않으면 실패에 해시가 붙어
        // 다음 호출이 "같은 지표니 다시 안 묻는다"로 영영 건너뛴다
        assertThat(verdict.llmAnswered()).isFalse();
    }

    @Test
    @DisplayName("읽을 수 없는 답도 '답한 것'이 아니다 — 해시로 굳으면 다시 물을 수 없다 (설계 I145)")
    void unreadableAnswerIsNotAnAnswer() {
        stub(new AtomicReference<>(), "이건 JSON 이 아니다");

        final var verdict = service.forecast(input(1_210_000_000L, 1_140_000_000L));

        assertThat(verdict.llmAnswered()).isFalse();
    }

    @Test
    @DisplayName("답을 받았으면 표시한다 — 그때만 해시로 굳힌다 (설계 I145)")
    void answeredIsMarked() {
        stub(new AtomicReference<>(), """
                {"direction":"DOWN","confidence":"MEDIUM","factors":[
                  {"name":"실거래 추세","effect":"DOWN","weight":"HIGH",
                   "evidence":"직전 3개월 중앙값 12억 1,000만원 → 최근 11억 4,000만원"}],
                 "caveats":[]}
                """);

        final var verdict = service.forecast(input(1_210_000_000L, 1_140_000_000L));

        assertThat(verdict.llmAnswered()).isTrue();
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

    /**
     * <b>AI 가 방향을 말했으면 그대로 따릅니다</b> (설계 I249).
     *
     * <p>지표를 세면 하락인데 AI 는 상승이라 했습니다. 결론은 <b>상승</b>입니다 —
     * 우리가 세는 것은 AI 가 <b>방향을 안 말했을 때</b>뿐입니다.
     */
    @Test
    @DisplayName("AI 가 방향을 말하면 지표와 달라도 그대로 따른다 (설계 I249)")
    void followsTheModelWhenItCommits() {
        stub(new AtomicReference<>(), """
                {"direction":"UP","confidence":"HIGH",
                 "factors":[
                   {"name":"금리 국면","effect":"DOWN","weight":"MEDIUM","evidence":"금리가 오릅니다"},
                   {"name":"전세가율","effect":"DOWN","weight":"MEDIUM","evidence":"전세가율이 낮습니다"},
                   {"name":"장기 추세","effect":"UP","weight":"MEDIUM","evidence":"완만히 올랐습니다"}],
                 "summary":"","caveats":[]}""");

        final var verdict = service.forecast(input(1_210_000_000L, 1_140_000_000L));

        assertThat(verdict.conclusion().direction()).isEqualTo(ForecastDirection.UP);
        assertThat(verdict.llmDirection())
                .as("AI 가 뭐라 했는지 따로 남겨야 '유력'을 가릴 수 있다")
                .isEqualTo(ForecastDirection.UP);
    }

    /**
     * <b>AI 가 판단을 보류하면 우리가 셉니다</b> (설계 I249).
     *
     * <p>실제로 겪은 화면입니다 — 지표는 ▲▼▼▲ 인데 결론만 판단 보류였습니다.
     * 세어 보면 2:2 동수이고, <b>동수면 상승</b>입니다.
     */
    @Test
    @DisplayName("AI 가 보류하면 지표를 세어 정한다 — 동수면 상승 (설계 I249)")
    void countsWhenTheModelAbstains() {
        stub(new AtomicReference<>(), """
                {"direction":"UNCERTAIN","confidence":"LOW",
                 "factors":[
                   {"name":"장기 가격 추세","effect":"UP","weight":"MEDIUM","evidence":"완만히 올랐습니다"},
                   {"name":"금리 국면","effect":"DOWN","weight":"MEDIUM","evidence":"금리가 오릅니다"},
                   {"name":"낮은 전세가율","effect":"DOWN","weight":"MEDIUM","evidence":"전세가율이 낮습니다"},
                   {"name":"정비 기대","effect":"UP","weight":"LOW","evidence":"노후 대단지입니다"}],
                 "summary":"","caveats":[]}""");

        final var verdict = service.forecast(input(1_210_000_000L, 1_140_000_000L));

        assertThat(verdict.conclusion().direction())
                .as("무게로 세면 하락(4:3)이지만 머릿수로는 2:2 동수다")
                .isEqualTo(ForecastDirection.UP);
        assertThat(verdict.llmDirection()).isEqualTo(ForecastDirection.UNCERTAIN);
        assertThat(verdict.conclusion().confidence())
                .as("우리가 대신 정했으면 확신도를 낮춘다")
                .isEqualTo(banghak.home.halley.domain.forecast.ForecastConfidence.LOW);
    }

    /**
     * <b>유지도 "방향을 말한 것"이 아닙니다</b> (설계 I248 · I249).
     *
     * <p>규칙 1에서 <b>유지 = 판단 보류</b>로 합쳤습니다. 그러니 AI 가 `FLAT` 을 내도
     * 우리가 셉니다 — 파서가 AI 의 "모르겠다"를 `FLAT` 으로 바꿔 넣고 있어([I247])
     * `FLAT` 은 이미 <b>모름의 하치장</b>입니다.
     */
    @Test
    @DisplayName("AI 가 유지라 해도 지표를 센다 — 유지는 방향을 말한 것이 아니다 (설계 I249)")
    void countsWhenTheModelSaysFlat() {
        stub(new AtomicReference<>(), """
                {"direction":"FLAT","confidence":"HIGH",
                 "factors":[
                   {"name":"장기 추세","effect":"UP","weight":"MEDIUM","evidence":"올랐습니다"},
                   {"name":"실거래 추세","effect":"UP","weight":"HIGH","evidence":"오릅니다"},
                   {"name":"금리 국면","effect":"DOWN","weight":"MEDIUM","evidence":"금리가 오릅니다"}],
                 "summary":"","caveats":[]}""");

        final var verdict = service.forecast(input(1_210_000_000L, 1_140_000_000L));

        assertThat(verdict.conclusion().direction())
                .as("유지를 결론으로 받아들이면 판단 보류가 다시 쌓인다")
                .isEqualTo(ForecastDirection.UP);
        assertThat(verdict.llmDirection()).isEqualTo(ForecastDirection.FLAT);
        assertThat(verdict.conclusion().confidence())
                .as("우리가 대신 정했으면 확신도를 낮춘다")
                .isEqualTo(banghak.home.halley.domain.forecast.ForecastConfidence.LOW);
    }

    @Test
    @DisplayName("실거래 표본이 없으면 지표를 세어 말하되 확신도는 낮춘다 (설계 I234)")
    void fallsBackToAMajorityReadWhenSamplesTooFew() {
        stub(new AtomicReference<>(), """
                {"direction":"UP","confidence":"HIGH",
                 "factors":[{"name":"금리 국면","effect":"UP","weight":"MEDIUM",
                   "evidence":"금리가 내리는 중입니다"}],
                 "summary":"","caveats":[]}""");

        // 달마다 1건뿐 — 실거래 추세도 장기 추세도 안 나온다
        final var verdict = service.forecast(thinInput());

        // 한때는 여기서 UNCERTAIN 이었다. 그랬더니 거의 모든 매물이 판단 보류였고,
        // 금리 같은 지표가 나와 있는데도 그랬다 — 알아낸 것을 안 보여 준 셈이다 (설계 I234)
        assertThat(verdict.conclusion().direction()).isEqualTo(ForecastDirection.UP);
        assertThat(verdict.conclusion().confidence())
                .isEqualTo(banghak.home.halley.domain.forecast.ForecastConfidence.LOW);
        assertThat(verdict.conclusion().caveats())
                .anyMatch(c -> c.contains("표본이 3건 미만"))
                .anyMatch(c -> c.contains("확신이 있어서가 아닙니다"));
    }

    /**
     * LLM 요인이 전부 걸러지면 <b>규칙 예측을 그대로 씁니다</b> — 다수결(설계 I234)보다
     * 앞에 있는 규칙입니다. 지어낸 숫자만 인용한 답은 믿을 수 없기 때문입니다.
     *
     * <p>셀 것이 <b>정말로</b> 하나도 없는 경우는 `MajorityDirectionTest` 가 봅니다.
     */
    @Test
    @DisplayName("LLM 요인이 비면 규칙 예측을 쓴다 — 다수결보다 앞선 규칙이다")
    void emptyLlmFactorsFallBackToRules() {
        stub(new AtomicReference<>(), """
                {"direction":"DOWN","confidence":"HIGH","factors":[],"summary":"","caveats":[]}""");

        final var verdict = service.forecast(thinInput());

        // LLM 은 DOWN 이라 했지만 요인이 없어 버려졌다 — 규칙 예측(금리 국면 UP)이 남는다
        assertThat(verdict.conclusion()).isEqualTo(verdict.byCode());
        assertThat(verdict.conclusion().direction()).isEqualTo(ForecastDirection.UP);
    }

    /**
     * 3개월 창이 얇은 것과 실거래 자료가 없는 것은 <b>다른 얘기다</b> (설계 I151).
     *
     * <p>§2.2-A 의 취지는 "3건으로는 누구도 알 수 없다"인데, 실거래 추세 하나만 보면
     * <b>장기 표본이 넉넉해도 최근 석 달이 한산하면</b> 판단이 덮인다.
     */
    @Test
    @DisplayName("최근 3개월이 얇아도 장기 추세가 나왔으면 LLM 판단을 그대로 쓴다 (설계 I151)")
    void keepsLlmVerdictWhenLongTermTrendExists() {
        stub(new AtomicReference<>(), """
                {"direction":"UP","confidence":"MEDIUM",
                 "factors":[{"name":"장기 추세","effect":"UP","weight":"MEDIUM",
                   "evidence":"4년에 걸쳐 올랐습니다"}],
                 "summary":"","caveats":[]}""");

        final var verdict = service.forecast(longTermOnlyInput());

        // 코드가 덮지 않는다 — 실거래를 세는 지표가 하나는 나왔다
        assertThat(verdict.conclusion().direction()).isEqualTo(ForecastDirection.UP);
        assertThat(verdict.byCode().factors())
                .extracting(banghak.home.halley.domain.forecast.PriceFactor::name)
                .contains("장기 추세")
                .doesNotContain("실거래 추세");
    }

    /**
     * 실거래를 <b>안 세는</b> 지표만 나온 경우 (설계 I151).
     *
     * <p>금리 국면은 ECOS 통계라 아무리 나와도 <b>이 매물의 표본</b>과는 무관하다.
     * 그것으로 안전장치를 통과시키면 취지가 무너진다.
     */
    @Test
    @DisplayName("금리 국면만으로는 확신하지 않는다 — 방향은 세어 말하고 확신도를 낮춘다 (설계 I151 · I234)")
    void rateCycleAloneDoesNotEarnConfidence() {
        stub(new AtomicReference<>(), """
                {"direction":"UP","confidence":"HIGH",
                 "factors":[{"name":"금리 국면","effect":"UP","weight":"MEDIUM",
                   "evidence":"금리가 내리는 중입니다"}],
                 "summary":"","caveats":[]}""");

        final var verdict = service.forecast(thinInput());

        assertThat(verdict.byCode().factors())
                .extracting(banghak.home.halley.domain.forecast.PriceFactor::name)
                .contains("금리 국면");
        // 그건 이 매물의 실거래가 아니다 — 방향은 말하되 <b>확신도는 LOW</b> 다
        assertThat(verdict.conclusion().confidence())
                .isEqualTo(banghak.home.halley.domain.forecast.ForecastConfidence.LOW);
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
        // 키를 나중에 넣으면 다시 물어야 한다 (설계 I145)
        assertThat(verdict.llmAnswered()).isFalse();
    }

    /**
     * 저장할 해시 (설계 I145).
     *
     * <p>이것이 <b>화면에서 다시 시킬 유일한 길</b>을 지킨다. 판단 보류는 화살표가 없어
     * 모달을 못 열고 매매가 클릭이 전부인데, 실패에 해시가 붙으면 눌러도 건너뛴다.
     */
    @Test
    @DisplayName("실패한 호출에는 해시를 남기지 않는다 — 남기면 다시 눌러도 건너뛴다 (설계 I145)")
    void noHashWhenLlmDidNotAnswer() {
        // given — 프롬프트는 만들어졌지만 답을 못 받았다 (400·키 없음·읽을 수 없는 답)
        final var outlook = PriceOutlook.uncertain(12, List.of());
        final var prompt = ForecastPrompt.of(property(), List.of(), 12);

        final var failed = new PriceForecastService.ForecastVerdict(outlook, outlook, null, prompt, false);
        final var answered = new PriceForecastService.ForecastVerdict(outlook, outlook, null, prompt, true);

        // then — 프롬프트가 있다고 해시를 남기면 안 된다. 그 둘은 다른 얘기다
        assertThat(PriceForecastService.hashToStore(failed)).isNull();
        assertThat(PriceForecastService.hashToStore(answered)).isNotNull();

        // 모델도 비워 둔다. "claude가 냈다"고 적으면 사후 검증이 호출 실패를
        // 모델의 판단으로 센다 — 그리고 정리 SQL 이 model IS NULL 로 실패분을 고른다
        assertThat(PriceForecastService.modelToStore(failed, "claude")).isNull();
        assertThat(PriceForecastService.modelToStore(answered, "claude")).isEqualTo("claude");
    }

    /**
     * <b>규칙을 고치면 해시가 달라져야 합니다</b> (설계 I250).
     *
     * <p>[I59]의 "같은 입력이면 다시 안 묻는다"가 <b>입력이 같아도 규칙이 바뀌면
     * 다시 내야 한다</b>는 경우를 못 가렸습니다. 프롬프트만 해싱하면 판정 규칙을
     * 아무리 고쳐도 해시가 같아, <b>새 판정을 계산해 놓고 버립니다.</b>
     */
    @Test
    @DisplayName("해시에 판정 규칙 판 번호가 섞여 있다 (설계 I250)")
    void hashCarriesTheRulesVersion() {
        final var outlook = PriceOutlook.uncertain(12, List.of());
        final var prompt = ForecastPrompt.of(property(), List.of(), 12);
        final var verdict = new PriceForecastService.ForecastVerdict(
                outlook, outlook, ForecastDirection.UP, prompt, true);

        final String hash = PriceForecastService.hashToStore(verdict);

        assertThat(hash)
                .as("프롬프트만 해싱하면 규칙을 고쳐도 옛 결론이 그대로 남는다")
                .isNotEqualTo(PriceForecastService.sha256(prompt.full()));
    }

    @Test
    @DisplayName("아예 묻지 않았어도 해시는 없다 — 프롬프트 자체가 없다")
    void noHashWhenNeverAsked() {
        final var outlook = PriceOutlook.uncertain(12, List.of());
        assertThat(PriceForecastService.hashToStore(
                new PriceForecastService.ForecastVerdict(outlook, outlook, null, null, false))).isNull();
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
    /**
     * 장기 표본은 넉넉한데 <b>최근 석 달만</b> 한산한 경우 (설계 I151).
     *
     * <p>실거래 추세(3개월 창)는 안 나오고 장기 추세(12개월 창 둘)는 나온다 —
     * 이것이 §2.2-A 를 좁게 구현했을 때 애꿎게 덮이던 자리다.
     */
    private ForecastInput longTermOnlyInput() {
        final List<MonthlyTrades> months = new ArrayList<>();
        for (int m = 61; m >= 1; m--) {
            // 최근 3개월(1·2·3개월 전)에 1 + 1 + 0 = 2건만. 나머지 달은 3건씩
            final int count = switch (m) {
                case 1, 2 -> 1;
                case 3 -> 0;
                default -> 3;
            };
            // 4년에 걸쳐 오른다 — 장기 추세가 UP 을 낸다
            final long price = m >= 49 ? 1_000_000_000L : 1_200_000_000L;
            months.add(month(YearMonth.now().minusMonths(m), price, count));
        }
        return new ForecastInput(property(), months, List.of(), List.of(), List.of(), null);
    }

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
