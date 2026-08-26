package banghak.home.halley.adapter.outbound.external.odsay;

import banghak.home.halley.domain.scoring.TransitResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class OdsayTransitAdapterTest {

    private static final String TRANSIT_JSON = """
            {"result":{"path":[{"info":{
                "totalTime":5400,"walkTime":600,"subwayTransitCount":1,"busTransitCount":1
            }}]}}
            """;
    private static final String EMPTY_PATH_JSON = "{\"result\":{\"path\":[]}}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("대중교통 조회는 Feign 응답을 분 단위 TransitResult로 변환해 반환한다")
    void findTransitReturnsParsedResult() {
        // given
        final OdsayTransitFeignClient client = stubClient(TRANSIT_JSON);
        final OdsayTransitAdapter adapter = new OdsayTransitAdapter(client, "key", objectMapper);

        // when
        final TransitResult result = adapter.findTransit(126.9, 37.5, 127.0, 37.5);

        // then
        assertThat(result.isComputed()).isTrue();
        assertThat(result.totalMinutes()).isEqualTo(90);
        assertThat(result.walkMinutes()).isEqualTo(10);
        assertThat(result.transferCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("경로 정보가 없으면 MISSING으로 반환한다")
    void emptyPathReturnsMissing() {
        // given
        final OdsayTransitFeignClient client = stubClient(EMPTY_PATH_JSON);
        final OdsayTransitAdapter adapter = new OdsayTransitAdapter(client, "key", objectMapper);

        // when
        final TransitResult result = adapter.findTransit(126.9, 37.5, 127.0, 37.5);

        // then
        assertThat(result.isComputed()).isFalse();
    }

    @Test
    @DisplayName("Feign 폴백(실패) 또는 키 부재 시 MISSING으로 우아하게 처리한다")
    void fallbackOrMissingKeyReturnsMissing() {
        // given
        final OdsayTransitAdapter fallbackAdapter = new OdsayTransitAdapter(stubClient(null), "key", objectMapper);
        final OdsayTransitAdapter noKeyAdapter = new OdsayTransitAdapter(stubClient(TRANSIT_JSON), "  ", objectMapper);

        // when / then
        assertThat(fallbackAdapter.findTransit(126.9, 37.5, 127.0, 37.5).isComputed()).isFalse();
        assertThat(noKeyAdapter.findTransit(126.9, 37.5, 127.0, 37.5).isComputed()).isFalse();
    }

    private static OdsayTransitFeignClient stubClient(String json) {
        return new OdsayTransitFeignClient() {
            @Override
            public String findTransit(String apiKey, double startX, double startY, double endX, double endY) {
                return json;
            }
        };
    }
}
