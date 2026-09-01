package banghak.home.halley.adapter.outbound.external.slack;

import banghak.home.halley.application.port.out.external.SlackPort;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slack 웹훅이 <b>실제로 나가는지</b> (설계 I175).
 *
 * <p>운영에서 이렇게 터졌습니다.
 *
 * <pre>
 * EncodeException: Invalid mime type "{Content-Type}": does not contain '/'
 * </pre>
 *
 * <p><b>Feign 설정은 목(mock)으로 검증되지 않습니다.</b> 어댑터를 단위 테스트해 봐야
 * 인터페이스 호출만 확인할 뿐이고, 문제는 <b>Feign 이 그 인터페이스를 어떻게 HTTP 로
 * 바꾸느냐</b>에 있었습니다. 그래서 진짜 서버를 세우고 <b>받은 것</b>을 봅니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Slack 웹훅 전송 (설계 I175)")
class SlackWebhookClientTest {

    @Autowired
    private SlackPort slackPort;

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 18099), 0);
        server.createContext("/", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            final byte[] ok = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("Content-Type이 자리표시자가 아니라 실제 값으로 나간다")
    void sendsRealContentType() {
        final boolean sent = slackPort.send("http://localhost:18099/services/T/B/x", "안녕");

        assertThat(sent).isTrue();
        // "{Content-Type}" 이 그대로 나가면 인코더가 터진다
        assertThat(contentType.get()).contains("application/json");
    }

    /**
     * <b>이 테스트는 charset 선언이 필요하다는 것을 증명하지는 않습니다.</b>
     * `consumes` 에서 charset 을 빼고 돌려 봤더니 <b>여전히 UTF-8 로 나갔습니다</b> —
     * `application/json` 이면 Jackson 컨버터가 쓰기 때문입니다.
     *
     * <p>그래도 이 테스트는 값어치가 있습니다. 인코더나 컨버터가 바뀌어
     * <b>한글이 깨지기 시작하면</b> 여기서 걸립니다.
     */
    @Test
    @DisplayName("한글이 UTF-8로 나간다")
    void sendsKoreanAsUtf8() {
        slackPort.send("http://localhost:18099/services/T/B/x", ":house: 상계주공7단지 등록");

        assertThat(body.get()).contains("상계주공7단지");
        assertThat(body.get()).doesNotContain("?");
    }

    @Test
    @DisplayName("본문은 Slack이 읽는 {\"text\":...} 모양이다")
    void sendsSlackTextPayload() {
        slackPort.send("http://localhost:18099/services/T/B/x", "한 줄");

        assertThat(body.get()).startsWith("{\"text\":");
    }
}
