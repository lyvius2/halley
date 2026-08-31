package banghak.home.halley.domain.forecast;

import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("전망 답 읽기와 안전장치 (설계 I134)")
class ForecastVerdictParserTest {

    private final ForecastVerdictParser parser = new ForecastVerdictParser(new ObjectMapper());

    private final ForecastPrompt prompt = ForecastPrompt.of(property(), List.of(
            new PriceFactor("실거래 추세", ForecastDirection.DOWN, FactorWeight.HIGH,
                    "직전 3개월 중앙값 12억 1,000만원 → 최근 3개월 11억 4,000만원 (-5.8%, 표본 8건 → 9건)"),
            new PriceFactor("금리 국면", ForecastDirection.UP, FactorWeight.MEDIUM,
                    "가계대출금리 5.50% (2023-01) → 3.40% (2026-01), -2.10%p")), 12);

    @Test
    @DisplayName("정상 답을 읽는다")
    void parsesNormalVerdict() {
        final var outlook = parser.parse("""
                {"direction":"DOWN","confidence":"MEDIUM",
                 "factors":[{"name":"실거래 추세","effect":"DOWN","weight":"HIGH",
                   "evidence":"최근 3개월 중앙값이 11억 4,000만원으로 내렸습니다"}],
                 "summary":"실거래가 6개월째 약세입니다.",
                 "caveats":["정책 변화는 알 수 없습니다"]}
                """, prompt, 12).orElseThrow();

        assertThat(outlook.direction()).isEqualTo(ForecastDirection.DOWN);
        assertThat(outlook.confidence()).isEqualTo(ForecastConfidence.MEDIUM);
        assertThat(outlook.factors()).hasSize(1);
        assertThat(outlook.caveats()).contains("실거래가 6개월째 약세입니다.");
    }

    @Test
    @DisplayName("우리가 주지 않은 숫자를 인용한 요인은 버린다 — 환각을 잡는 실질적 장치")
    void dropsFactorsCitingInventedNumbers() {
        final var outlook = parser.parse("""
                {"direction":"UP","confidence":"HIGH",
                 "factors":[
                   {"name":"실거래 추세","effect":"DOWN","weight":"HIGH",
                    "evidence":"최근 3개월 중앙값이 11억 4,000만원입니다"},
                   {"name":"인근 개발","effect":"UP","weight":"HIGH",
                    "evidence":"2027년 착공 예정인 8만평 규모 복합단지가 있습니다"}],
                 "summary":"","caveats":[]}
                """, prompt, 12).orElseThrow();

        // 2027·8 은 프롬프트에 없다 — 지어낸 것이다
        assertThat(outlook.factors()).extracting(PriceFactor::name).containsExactly("실거래 추세");
    }

    @Test
    @DisplayName("근거가 없는 요인은 버린다")
    void dropsFactorsWithoutEvidence() {
        final var outlook = parser.parse("""
                {"direction":"FLAT","confidence":"LOW",
                 "factors":[{"name":"느낌","effect":"UP","weight":"HIGH","evidence":""},
                            {"name":"이름만","effect":"UP","weight":"HIGH"}],
                 "summary":"","caveats":[]}
                """, prompt, 12).orElseThrow();

        assertThat(outlook.factors()).isEmpty();
    }

    @Test
    @DisplayName("모르는 direction은 UNCERTAIN, 모르는 confidence는 LOW — 모를 때는 보수적으로")
    void unknownEnumsFallBackConservatively() {
        final var outlook = parser.parse("""
                {"direction":"STRONG_BUY","confidence":"VERY_HIGH","factors":[],
                 "summary":"","caveats":[]}
                """, prompt, 12).orElseThrow();

        assertThat(outlook.direction()).isEqualTo(ForecastDirection.UNCERTAIN);
        assertThat(outlook.confidence()).isEqualTo(ForecastConfidence.LOW);
    }

    @Test
    @DisplayName("요인의 effect가 UNCERTAIN이면 FLAT으로 본다 — 요인 하나가 '모른다'일 수는 없다")
    void factorEffectCannotBeUncertain() {
        final var outlook = parser.parse("""
                {"direction":"FLAT","confidence":"LOW",
                 "factors":[{"name":"금리 국면","effect":"UNCERTAIN","weight":"MEDIUM",
                   "evidence":"가계대출금리 3.40%입니다"}],
                 "summary":"","caveats":[]}
                """, prompt, 12).orElseThrow();

        assertThat(outlook.factors().getFirst().effect()).isEqualTo(ForecastDirection.FLAT);
    }

    @Test
    @DisplayName("앞뒤에 설명이나 코드펜스가 붙어도 읽는다")
    void toleratesSurroundingText() {
        final var outlook = parser.parse("""
                판단 결과입니다.
                ```json
                {"direction":"UP","confidence":"LOW","factors":[],"summary":"","caveats":[]}
                ```
                도움이 되었길 바랍니다.
                """, prompt, 12).orElseThrow();

        assertThat(outlook.direction()).isEqualTo(ForecastDirection.UP);
    }

    @Test
    @DisplayName("JSON이 아니면 읽지 않는다")
    void rejectsNonJson() {
        assertThat(parser.parse("죄송합니다. 판단할 수 없습니다.", prompt, 12)).isEmpty();
        assertThat(parser.parse(null, prompt, 12)).isEmpty();
        assertThat(parser.parse("{망가진", prompt, 12)).isEmpty();
    }

    @Test
    @DisplayName("유의사항이 비면 채운다 — 비워 두면 모든 것을 봤다고 여긴다")
    void fillsEmptyCaveats() {
        final var outlook = parser.parse("""
                {"direction":"UP","confidence":"LOW","factors":[],"summary":"오를 것 같습니다.","caveats":[]}
                """, prompt, 12).orElseThrow();

        assertThat(outlook.caveats()).anyMatch(c -> c.contains("정책 변화"));
    }

    @Test
    @DisplayName("한 자리 수는 봐준다 — '3개월'·'2건' 같은 말까지 막으면 멀쩡한 근거가 버려진다")
    void allowsSingleDigits() {
        final var outlook = parser.parse("""
                {"direction":"DOWN","confidence":"LOW",
                 "factors":[{"name":"실거래 추세","effect":"DOWN","weight":"HIGH",
                   "evidence":"최근 3개월 동안 약세이며 표본은 9건입니다"}],
                 "summary":"","caveats":[]}
                """, prompt, 12).orElseThrow();

        assertThat(outlook.factors()).hasSize(1);
    }

    @Test
    @DisplayName("프롬프트에 코드의 종합 예측을 넣지 않는다 — 넣으면 모델이 끌려간다")
    void promptDoesNotLeakCodePrediction() {
        assertThat(prompt.user()).contains("실거래 추세").contains("금리 국면");
        // 코드가 낸 종합 방향·확신도는 어디에도 없어야 한다
        assertThat(prompt.user()).doesNotContain("규칙 기반").doesNotContain("코드 예측");
    }

    @Test
    @DisplayName("원본 거래 목록을 넣지 않는다 — 넣으면 모델이 산술을 하게 된다")
    void promptCarriesOnlyComputedValues() {
        assertThat(prompt.user()).contains("계산된 지표");
        assertThat(prompt.user()).contains("다시 계산하지 마라");
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
