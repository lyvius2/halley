package banghak.home.halley.adapter.outbound.external.transit;

import banghak.home.halley.adapter.outbound.external.claude.LlmAvailability;
import banghak.home.halley.application.port.out.external.LlmPort;
import banghak.home.halley.application.service.LlmModelService;
import banghak.home.halley.domain.llm.LlmMessage;
import banghak.home.halley.domain.llm.LlmResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 열린 차단기에 <b>다시 던지지 않는다</b> (설계 I271).
 *
 * <p>운영 로그가 몇 분 동안 같은 줄로 찼습니다.
 *
 * <pre>
 * CircuitBreaker 'claude-llm' is OPEN and does not permit further calls
 *   → "LLM is busy - waiting 2000ms and asking once more."
 *   → 또 OPEN → 또 2초 대기 → …
 * </pre>
 *
 * <p>어댑터가 모든 실패를 {@code "call failed"} 하나로 뭉개서 <b>"붐빈다"와
 * "차단됐다"를 구별하지 못했습니다.</b>
 */
@DisplayName("LLM 차단기 (설계 I271)")
class LlmTransitCircuitTest {

    private final AtomicInteger calls = new AtomicInteger();

    @Test
    @DisplayName("차단됐으면 한 번도 안 묻는다")
    void doesNotAskWhileTheCircuitIsOpen() {
        final LlmAvailability availability = new LlmAvailability();
        availability.recordIfBlocked(new IllegalStateException(
                "CircuitBreaker 'claude-llm' is OPEN and does not permit further calls"));

        final var estimator = estimator(availability);
        final var answers = estimator.estimate(List.of(
                new LlmTransitEstimator.Leg("a", 127.0, 37.5, 127.1, 37.6)));

        assertThat(answers).isEmpty();
        assertThat(calls.get())
                .as("열린 차단기에 던지면 성공할 리 없는데 2초씩 기다렸다")
                .isZero();
    }

    @Test
    @DisplayName("붐비는 것은 한 번 더 묻는다 — 그건 차단이 아니다")
    void stillRetriesWhenMerelyBusy() {
        final var estimator = estimator(new LlmAvailability());

        estimator.estimate(List.of(new LlmTransitEstimator.Leg("a", 127.0, 37.5, 127.1, 37.6)));

        // 붐빔까지 안 물으면 <b>일시적인 실패 하나로</b> 추정을 통째로 버린다 (설계 I218)
        assertThat(calls.get()).as("붐빌 때는 한 번 더 물어야 한다").isEqualTo(2);
    }

    @Test
    @DisplayName("도중에 차단되면 남은 묶음은 안 묻는다")
    void stopsWhenTheCircuitOpensMidWay() {
        final LlmAvailability availability = new LlmAvailability();
        final LlmPort port = new LlmPort() {
            @Override
            public String provider() {
                return "stub";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public LlmResult complete(LlmMessage message) {
                calls.incrementAndGet();
                // 첫 호출에서 차단기가 열린다
                availability.recordIfBlocked(new IllegalStateException(
                        "CircuitBreaker 'claude-llm' is OPEN and does not permit further calls"));
                return LlmResult.failed("call failed");
            }
        };
        final var estimator = new LlmTransitEstimator(port, new ObjectMapper(), llmModels(), availability);

        final List<LlmTransitEstimator.Leg> many = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            many.add(new LlmTransitEstimator.Leg("leg" + i, 127.0, 37.5, 127.1, 37.6));
        }
        estimator.estimate(many);

        // 묶음이 여럿이라도 <b>차단된 뒤로는</b> 안 묻는다
        assertThat(calls.get())
                .as("차단된 뒤에도 묶음마다 계속 물었다 — %d회", calls.get())
                .isEqualTo(1);
    }

    private LlmTransitEstimator estimator(LlmAvailability availability) {
        final LlmPort port = new LlmPort() {
            @Override
            public String provider() {
                return "stub";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public LlmResult complete(LlmMessage message) {
                calls.incrementAndGet();
                return LlmResult.failed("call failed");
            }
        };
        return new LlmTransitEstimator(port, new ObjectMapper(), llmModels(), availability);
    }

    /** 이 시험은 차단기만 본다 — 모델은 안 고른 셈 치고 기본값에 맡긴다 (설계 I267). */
    private static LlmModelService llmModels() {
        final var models = mock(LlmModelService.class);
        when(models.modelFor(any())).thenReturn(null);
        return models;
    }
}
