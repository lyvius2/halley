package banghak.home.halley.adapter.outbound.external.kakao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 하루치를 다 쓴 것을 <b>기억하는가</b> (설계 I270).
 *
 * <p>운영 로그에서 이렇게 났습니다.
 *
 * <pre>
 * {"code":-10,"msg":"API limit has been exceeded."}
 *   → CircuitBreaker 'kakao-directions' is OPEN
 * </pre>
 */
@DisplayName("길찾기 하루 한도 (설계 I270)")
class DirectionsQuotaTest {

    @Test
    @DisplayName("한도를 보면 그날은 다 썼다고 본다")
    void remembersTheLimit() {
        final DirectionsQuota quota = new DirectionsQuota();
        assertThat(quota.exhausted()).isFalse();

        quota.recordIfExhausted(new IllegalStateException(
                "[400] ... [{\"code\":-10,\"msg\":\"API limit has been exceeded.\"}]"));

        assertThat(quota.exhausted()).isTrue();
    }

    @Test
    @DisplayName("차단기가 열린 것은 한도가 아니다 — 그건 결과다")
    void doesNotConfuseTheBreakerWithTheLimit() {
        final DirectionsQuota quota = new DirectionsQuota();

        quota.recordIfExhausted(new IllegalStateException(
                "CircuitBreaker 'kakao-directions' is OPEN and does not permit further calls"));

        // 여기서 다 썼다고 보면 <b>일시적인 장애 하나로 그날을 통째로</b> 접는다
        assertThat(quota.exhausted()).isFalse();
    }

    @Test
    @DisplayName("원인이 겹겹이 싸여 있어도 찾아낸다")
    void looksThroughTheCauseChain() {
        final DirectionsQuota quota = new DirectionsQuota();

        quota.recordIfExhausted(new RuntimeException("wrapper",
                new IllegalStateException("API limit has been exceeded.")));

        assertThat(quota.exhausted()).isTrue();
    }

    @Test
    @DisplayName("다 썼으면 카카오를 부르지 않는다")
    void stopsCallingOnceExhausted() {
        final DirectionsQuota quota = new DirectionsQuota();
        final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        final KakaoDirectionsAdapter adapter = new KakaoDirectionsAdapter(
                new KakaoDirectionsFeignClient() {
                    @Override
                    public String directions(String origin, String destination, String priority) {
                        calls.incrementAndGet();
                        return null;
                    }

                    @Override
                    public String futureDirections(String origin, String destination,
                                                   String priority, String departureTime) {
                        calls.incrementAndGet();
                        return null;
                    }
                }, "test-key", new tools.jackson.databind.ObjectMapper(), quota);

        adapter.findRoute(127.0, 37.5, 127.1, 37.6, null);
        assertThat(calls.get()).as("아직 한도를 안 봤으면 불러야 한다").isEqualTo(1);

        quota.recordIfExhausted(new IllegalStateException("API limit has been exceeded."));
        adapter.findRoute(127.0, 37.5, 127.1, 37.6, null);

        // 던져 봐야 400 이 오고, 차단기만 여닫힌다 — 로그가 49줄씩 쌓인다
        assertThat(calls.get()).as("다 썼는데 또 불렀다").isEqualTo(1);
    }
}
