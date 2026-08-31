package banghak.home.halley.adapter.outbound.external.odsay;

import banghak.home.halley.domain.scoring.TransitResult;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OdsayTransitAdapterTest {

    private static final String TRANSIT_JSON = """
            {"result":{"path":[{"info":{
                "totalTime":31,"totalWalk":680,"totalWalkTime":-1,"subwayTransitCount":1,"busTransitCount":0
            }}]}}
            """;
    private static final String TRANSIT_WITH_WALK_TIME_JSON = """
            {"result":{"path":[{"info":{
                "totalTime":25,"totalWalk":1200,"totalWalkTime":15,"subwayTransitCount":0,"busTransitCount":1
            }}]}}
            """;
    private static final String EMPTY_PATH_JSON = "{\"result\":{\"path\":[]}}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * <b>반환값만으로는 이 기능을 검증할 수 없습니다.</b> 오류든 경로 없음이든 결과는
     * 똑같이 미산출이라, 오류 처리를 통째로 지워도 반환값 테스트는 전부 통과합니다
     * (실제로 지워 보고 확인했습니다). 이 기능의 값어치는 <b>남는 로그</b>이므로
     * 로그를 본다 (설계 I141).
     */
    private ListAppender<ILoggingEvent> logs;
    private Logger adapterLogger;

    @BeforeEach
    void captureLogs() {
        adapterLogger = (Logger) LoggerFactory.getLogger(OdsayTransitAdapter.class);
        logs = new ListAppender<>();
        logs.start();
        adapterLogger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        adapterLogger.detachAppender(logs);
    }

    private List<String> warnings() {
        return logs.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Test
    @DisplayName("대중교통 조회는 ODsay 응답(totalTime 분 단위)을 TransitResult로 변환해 반환한다")
    void findTransitReturnsParsedResult() {
        // given
        final OdsayTransitFeignClient client = stubClient(TRANSIT_JSON);
        final OdsayTransitAdapter adapter = new OdsayTransitAdapter(client, "key", objectMapper);

        // when
        final TransitResult result = adapter.findTransit(126.9, 37.5, 127.0, 37.5);

        // then
        assertThat(result.isComputed()).isTrue();
        assertThat(result.totalMinutes()).isEqualTo(31);
        assertThat(result.walkMinutes()).isEqualTo(9);
        assertThat(result.transferCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("totalWalkTime이 제공되면 그 값을 도보분으로 사용하고, 없으면 totalWalk(미터)로 환산한다")
    void walkMinutesUsesWalkTimeOrMeters() {
        // given
        final OdsayTransitFeignClient client = stubClient(TRANSIT_WITH_WALK_TIME_JSON);
        final OdsayTransitAdapter adapter = new OdsayTransitAdapter(client, "key", objectMapper);

        // when
        final TransitResult result = adapter.findTransit(126.9, 37.5, 127.0, 37.5);

        // then
        assertThat(result.totalMinutes()).isEqualTo(25);
        assertThat(result.walkMinutes()).isEqualTo(15);
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

    @Test
    @DisplayName("오류 응답도 MISSING이다 — HTTP 200이라 폴백이 뜨지 않는다 (설계 I141)")
    void errorObjectReturnsMissing() {
        final OdsayTransitAdapter adapter = new OdsayTransitAdapter(
                stubClient("""
                        {"error":{"code":"-8","msg":"출발지와 도착지가 너무 가깝습니다."}}
                        """), "key", objectMapper);

        assertThat(adapter.findTransit(126.9, 37.5, 126.9001, 37.5001).isComputed()).isFalse();

        // 왜 안 나왔는지가 로그에 남아야 한다 — 이게 이 기능의 전부다
        assertThat(warnings()).singleElement().satisfies(line -> assertThat(line)
                .contains("code=-8")
                .contains("출발지와 도착지가 너무 가깝습니다")
                .contains("도보권"));
    }

    @Test
    @DisplayName("error가 배열로 와도 읽는다 — 엔드포인트마다 모양이 다르다 (설계 I141)")
    void errorArrayReturnsMissing() {
        final OdsayTransitAdapter adapter = new OdsayTransitAdapter(
                stubClient("""
                        {"error":[{"code":"500","msg":"검색 결과가 없습니다."}]}
                        """), "key", objectMapper);

        assertThat(adapter.findTransit(126.9, 37.5, 130.0, 37.5).isComputed()).isFalse();

        assertThat(warnings()).singleElement().satisfies(line -> assertThat(line)
                .contains("code=500")
                .contains("서비스 지역 밖"));
    }

    @Test
    @DisplayName("오류 응답을 정상으로 오해하지 않는다 — error가 있으면 경로를 보지 않는다")
    void errorWinsOverStalePath() {
        // ODsay 가 오류와 함께 빈 result 를 실어 보내도 미산출이어야 한다
        final OdsayTransitAdapter adapter = new OdsayTransitAdapter(
                stubClient("""
                        {"error":{"code":"3","msg":"LIMIT EXCEEDED"},"result":{"path":[]}}
                        """), "key", objectMapper);

        assertThat(adapter.findTransit(126.9, 37.5, 127.0, 37.5).isComputed()).isFalse();

        // 경로가 비었다는 로그가 아니라 오류 로그가 나와야 한다 — 원인이 다르다
        assertThat(warnings()).singleElement().satisfies(line -> assertThat(line)
                .contains("code=3")
                .contains("LIMIT EXCEEDED")
                // 코드만 남기면 결국 문서를 다시 뒤진다. 무엇을 해야 하는지가 같이 있어야 한다
                .contains("일일 사용량 초과")
                .doesNotContain("no usable path"));
    }

    @Test
    @DisplayName("오류가 아닌데 경로가 없으면 그것도 남긴다 — 응답 모양이 바뀐 것일 수 있다")
    void emptyPathIsAlsoLogged() {
        final OdsayTransitAdapter adapter =
                new OdsayTransitAdapter(stubClient(EMPTY_PATH_JSON), "key", objectMapper);

        adapter.findTransit(126.9, 37.5, 127.0, 37.5);

        assertThat(warnings()).singleElement().satisfies(line -> assertThat(line)
                .contains("no usable path")
                .contains("pathCount=0"));
    }

    @Test
    @DisplayName("정상 응답에는 경고를 남기지 않는다 — 매물마다 뜨면 로그가 못 쓰게 된다")
    void successIsQuiet() {
        new OdsayTransitAdapter(stubClient(TRANSIT_JSON), "key", objectMapper)
                .findTransit(126.9, 37.5, 127.0, 37.5);

        assertThat(warnings()).isEmpty();
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
