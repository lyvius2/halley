package banghak.home.halley.adapter.outbound.external.transit;

import banghak.home.halley.adapter.outbound.external.odsay.OdsayTransitAdapter;
import banghak.home.halley.application.port.out.external.LlmPort;
import banghak.home.halley.config.exception.TransitQuotaExceededException;
import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmResult;
import banghak.home.halley.domain.scoring.TransitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ODsay 가 하루치를 다 썼을 때 (설계 I210).
 *
 * <p>운영 로그에서 <b>`code=429, msg=Daily quota exceeded`</b> 가 줄줄이 났습니다.
 * 그때 직주근접은 통째로 미산출이 되고 임장 구간은 전부 999분이 됩니다.
 */
@DisplayName("ODsay 할당량 소진 시 LLM 폴백 (설계 I210)")
class TransitWithLlmFallbackTest {

    private static final String GOOD_ANSWER = """
            {"results":[{"id":"leg","totalMinutes":37,"transferCount":1,"walkMinutes":8,
              "legs":[{"kind":"WALK","minutes":5},
                      {"kind":"SUBWAY","lineName":"7호선","from":"상계","to":"강남구청","minutes":29}]}]}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OdsayTransitAdapter odsay;
    private final AtomicInteger llmCalls = new AtomicInteger();
    private String llmAnswer = GOOD_ANSWER;
    private boolean llmEnabled = true;

    private LlmPort llmPort() {
        return new LlmPort() {
            @Override
            public String provider() {
                return "stub";
            }

            @Override
            public boolean isEnabled() {
                return llmEnabled;
            }

            @Override
            public LlmResult complete(LlmMessage message) {
                llmCalls.incrementAndGet();
                return LlmResult.of(llmAnswer, "stub");
            }
        };
    }

    private TransitWithLlmFallback fallback() {
        return new TransitWithLlmFallback(odsay,
                new LlmTransitEstimator(llmPort(), objectMapper, ""));
    }

    @BeforeEach
    void setUp() {
        odsay = mock(OdsayTransitAdapter.class);
        when(odsay.isEnabled()).thenReturn(true);
        llmCalls.set(0);
        llmAnswer = GOOD_ANSWER;
        llmEnabled = true;
    }

    @Test
    @DisplayName("평소에는 ODsay 를 쓴다 — LLM 은 부르지 않는다")
    void usesOdsayWhenQuotaRemains() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new TransitResult(21, 0, 4));

        final TransitResult result = fallback().findTransit(126.9, 37.5, 127.0, 37.5);

        assertThat(result.totalMinutes()).isEqualTo(21);
        assertThat(result.estimated()).isFalse();
        assertThat(llmCalls).hasValue(0);
    }

    @Test
    @DisplayName("429 를 만나면 LLM 이 대신 답한다 — 미산출로 두지 않는다")
    void fallsBackToLlmOnQuota() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new TransitQuotaExceededException("code=429"));

        final TransitResult result = fallback().findTransit(126.9, 37.5, 127.0, 37.5);

        assertThat(result.totalMinutes()).isEqualTo(37);
        assertThat(result.transferCount()).isEqualTo(1);
        // <b>추정임이 값에 실려 있어야</b> 저장하는 쪽이 출처를 남길 수 있다
        assertThat(result.estimated()).isTrue();
        // 경로선 열쇠는 ODsay 것이라 없다 — 화면이 직선으로 그린다
        assertThat(result.mapObj()).isNull();
        assertThat(result.legs()).hasSize(2);
    }

    /**
     * 한 번 429 를 보면 <b>그날은 더 부르지 않습니다.</b>
     * 안 그러면 매물 하나 채점할 때마다 사람 수만큼 429 를 받으러 갑니다.
     */
    @Test
    @DisplayName("한 번 소진되면 그날은 ODsay 를 다시 두드리지 않는다")
    void stopsKnockingOnceExhausted() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new TransitQuotaExceededException("code=429"));
        final TransitWithLlmFallback port = fallback();

        port.findTransit(126.9, 37.5, 127.0, 37.5);
        port.findTransit(126.9, 37.5, 127.1, 37.6);
        port.findTransit(126.9, 37.5, 127.2, 37.7);

        verify(odsay, times(1)).findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertThat(port.estimating()).isTrue();
    }

    /**
     * 임장 행렬은 매물 8개면 64쌍입니다. 쌍마다 LLM 을 부르면 한 번 계산에 수십 분입니다.
     */
    @Test
    @DisplayName("여러 구간은 LLM 을 한 번만 부른다 — 쌍마다 부르면 못 쓴다")
    void batchAsksTheLlmOnce() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new TransitQuotaExceededException("code=429"));
        llmAnswer = """
                {"results":[{"id":"a","totalMinutes":10,"transferCount":0,"walkMinutes":3,"legs":[]},
                            {"id":"b","totalMinutes":20,"transferCount":1,"walkMinutes":5,"legs":[]},
                            {"id":"c","totalMinutes":30,"transferCount":0,"walkMinutes":7,"legs":[]}]}
                """;

        final Map<String, TransitResult> found = fallback().findTransitBatch(legs("a", "b", "c"));

        assertThat(found).containsOnlyKeys("a", "b", "c");
        assertThat(found.get("b").totalMinutes()).isEqualTo(20);
        assertThat(llmCalls).hasValue(1);
    }

    @Test
    @DisplayName("경로선은 LLM 에게 묻지 않는다 — 없는 길이 지도에 그려진다")
    void neverAsksTheLlmForCoordinates() {
        when(odsay.findLane("x")).thenThrow(new TransitQuotaExceededException("code=429"));

        assertThat(fallback().findLane("x").isEmpty()).isTrue();
        assertThat(llmCalls).hasValue(0);
    }

    /**
     * 소진을 안 뒤에는 <b>경로선도 두드리지 않습니다</b> (설계 I210).
     *
     * <p>구간마다 한 번씩 부르는 자리라, 안 막으면 임장 한 번에 429 를 수십 번
     * 받으러 갑니다 — 로그만 더러워지고 얻는 것이 없습니다.
     */
    @Test
    @DisplayName("소진된 뒤에는 경로선도 안 두드린다")
    void skipsLaneAfterExhaustion() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new TransitQuotaExceededException("code=429"));
        final TransitWithLlmFallback port = fallback();
        port.findTransit(126.9, 37.5, 127.0, 37.5);

        assertThat(port.findLane("x").isEmpty()).isTrue();
        assertThat(port.findLane("y").isEmpty()).isTrue();

        verify(odsay, never()).findLane(anyString());
    }

    /**
     * 0분은 <b>직주근접 만점</b>이 됩니다. "아주 가깝다"가 아니라 LLM 이 답을 못 낸
     * 것일 가능성이 큰데, 조용히 좋은 쪽으로 틀리는 값이 가장 위험합니다.
     */
    @Test
    @DisplayName("말이 안 되는 값은 버린다 — 0분·음수·너무 긴 값")
    void implausibleAnswersAreDropped() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new TransitQuotaExceededException("code=429"));
        llmAnswer = """
                {"results":[{"id":"a","totalMinutes":0},
                            {"id":"b","totalMinutes":-5},
                            {"id":"c","totalMinutes":9999},
                            {"id":"d","totalMinutes":null},
                            {"id":"e","totalMinutes":42}]}
                """;

        final Map<String, TransitResult> found = fallback().findTransitBatch(legs("a", "b", "c", "d", "e"));

        assertThat(found).containsOnlyKeys("e");
        assertThat(found.get("e").totalMinutes()).isEqualTo(42);
    }

    @Test
    @DisplayName("묻지 않은 id 는 버린다 — 지어낸 것이다")
    void unaskedIdsAreDropped() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new TransitQuotaExceededException("code=429"));
        llmAnswer = """
                {"results":[{"id":"a","totalMinutes":10},{"id":"몰래끼운것","totalMinutes":11}]}
                """;

        assertThat(fallback().findTransitBatch(legs("a"))).containsOnlyKeys("a");
    }

    @Test
    @DisplayName("LLM 이 엉뚱한 글을 주면 미산출로 둔다 — 999분으로 채우지 않는다")
    void garbageAnswerBecomesMissing() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new TransitQuotaExceededException("code=429"));
        llmAnswer = "죄송합니다, 경로를 찾을 수 없습니다.";

        assertThat(fallback().findTransit(126.9, 37.5, 127.0, 37.5).isComputed()).isFalse();
    }

    @Test
    @DisplayName("코드펜스를 둘러 와도 읽는다 — 두르지 말라고 해도 두른다")
    void tolerantOfCodeFences() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new TransitQuotaExceededException("code=429"));
        llmAnswer = "```json\n" + GOOD_ANSWER + "\n```";

        assertThat(fallback().findTransit(126.9, 37.5, 127.0, 37.5).totalMinutes()).isEqualTo(37);
    }

    @Test
    @DisplayName("LLM 도 없으면 산출할 수 없다고 말한다 — 부르는 쪽이 이유로 삼는다")
    void disabledWhenNeitherIsAvailable() {
        when(odsay.isEnabled()).thenReturn(false);
        llmEnabled = false;

        assertThat(fallback().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("ODsay 키가 없어도 LLM 이 있으면 산출할 수 있다")
    void enabledWhenOnlyLlmIsAvailable() {
        when(odsay.isEnabled()).thenReturn(false);

        assertThat(fallback().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("할당량이 남아 있으면 배치도 ODsay 로 돈다")
    void batchPrefersOdsay() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new TransitResult(11, 0, 2));

        final Map<String, TransitResult> found = fallback().findTransitBatch(legs("a", "b"));

        assertThat(found).containsOnlyKeys("a", "b");
        assertThat(found.get("a").estimated()).isFalse();
        assertThat(llmCalls).hasValue(0);
        verify(odsay, times(2)).findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("LLM 이 꺼져 있으면 부르지 않는다")
    void skipsLlmWhenDisabled() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new TransitQuotaExceededException("code=429"));
        llmEnabled = false;

        assertThat(fallback().findTransit(126.9, 37.5, 127.0, 37.5).isComputed()).isFalse();
        assertThat(llmCalls).hasValue(0);
    }

    private static Map<String, double[]> legs(String... ids) {
        final Map<String, double[]> legs = new LinkedHashMap<>();
        double offset = 0;
        for (final String id : ids) {
            legs.put(id, new double[]{126.9 + offset, 37.5, 127.0 + offset, 37.6});
            offset += 0.01;
        }
        return legs;
    }

    @Test
    @DisplayName("빈 요청에는 LLM 을 부르지 않는다")
    void emptyBatchAsksNothing() {
        assertThat(fallback().findTransitBatch(Map.of())).isEmpty();
        assertThat(llmCalls).hasValue(0);
        verify(odsay, never()).findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("모르는 leg 종류는 그 구간만 버린다 — 답 하나 때문에 전체가 날아가면 안 된다")
    void unknownLegKindDropsOnlyThatLeg() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new TransitQuotaExceededException("code=429"));
        llmAnswer = """
                {"results":[{"id":"leg","totalMinutes":25,"legs":[
                    {"kind":"HELICOPTER","minutes":5},
                    {"kind":"BUS","lineName":"146","minutes":20}]}]}
                """;

        final TransitResult result = fallback().findTransit(126.9, 37.5, 127.0, 37.5);

        assertThat(result.totalMinutes()).isEqualTo(25);
        assertThat(result.legs()).singleElement()
                .satisfies(leg -> assertThat(leg.lineName()).isEqualTo("146"));
    }

    @Test
    @DisplayName("ODsay 가 경로 없음을 주면 LLM 으로 넘어가지 않는다 — 물어도 소용없다")
    void noRouteIsNotAQuotaProblem() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(TransitResult.missing());

        assertThat(fallback().findTransit(126.9, 37.5, 127.0, 37.5).isComputed()).isFalse();
        assertThat(llmCalls).hasValue(0);
    }

    @Test
    @DisplayName("여러 답 중 하나만 실으면 나머지는 미산출 — 999분으로 채우지 않는다")
    void partialAnswersLeaveGaps() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new TransitQuotaExceededException("code=429"));
        llmAnswer = "{\"results\":[{\"id\":\"a\",\"totalMinutes\":15}]}";

        final Map<String, TransitResult> found = fallback().findTransitBatch(legs("a", "b", "c"));

        assertThat(found).containsOnlyKeys("a");
    }

    @Test
    @DisplayName("legs 를 안 주면 시간만 쓴다 — 채점은 총 시간만 본다")
    void missingLegsStillGivesMinutes() {
        when(odsay.findTransit(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new TransitQuotaExceededException("code=429"));
        llmAnswer = "{\"results\":[{\"id\":\"leg\",\"totalMinutes\":33}]}";

        final TransitResult result = fallback().findTransit(126.9, 37.5, 127.0, 37.5);

        assertThat(result.totalMinutes()).isEqualTo(33);
        assertThat(result.legs()).isEmpty();
        assertThat(List.of(result)).allMatch(TransitResult::estimated);
    }
}
