package banghak.home.halley.adapter.outbound.external;

import banghak.home.halley.adapter.outbound.external.kakao.KakaoDirectionsFallbackFactory;
import banghak.home.halley.adapter.outbound.external.kakao.KakaoLocalFeignFallbackFactory;
import banghak.home.halley.adapter.outbound.external.ministry.MinistryReferenceFallbackFactory;
import banghak.home.halley.adapter.outbound.external.odsay.OdsayTransitFallbackFactory;
import banghak.home.halley.adapter.outbound.external.slack.SlackWebhookFallbackFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackFactoryLoggingTest {

    @Test
    @DisplayName("원인이 없으면 '원인 미상'으로, 있으면 예외 타입과 메시지로 요약한다")
    void describesCause() {
        // then
        assertThat(FallbackCause.describe(null)).isEqualTo("원인 미상");
        assertThat(FallbackCause.describe(new IllegalStateException("불통")))
                .isEqualTo("IllegalStateException: 불통");
    }

    @Test
    @DisplayName("카카오 로컬 폴백은 빈 결과를 반환하면서 원인을 WARN으로 남긴다")
    void kakaoLocalFallbackLogsCause() {
        // given
        final ListAppender<ILoggingEvent> appender = attachAppender(KakaoLocalFeignFallbackFactory.class);
        final Throwable cause = new IllegalStateException("Unknown host");

        // when
        final String result = new KakaoLocalFeignFallbackFactory().create(cause).searchAddress("종로구");

        // then
        assertThat(result).isNull();
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("Unknown host");
        });
    }

    @Test
    @DisplayName("나머지 폴백도 예외를 던지지 않고 원인을 WARN으로 남긴다")
    void otherFallbacksLogCause() {
        // given
        final Throwable cause = new IllegalStateException("timeout");

        // when · then — 폴백은 호출부에 예외를 전파하지 않는다
        assertThat(new KakaoDirectionsFallbackFactory().create(cause)
                .directions("126.9,37.5", "127.0,37.4", "RECOMMEND")).isNull();
        assertThat(new OdsayTransitFallbackFactory().create(cause)
                .findTransit("key", 126.9, 37.5, 127.0, 37.4)).isNull();
        assertThat(new MinistryReferenceFallbackFactory().create(cause)
                .fetchTrade("key", "11110", "202607")).isNull();
        assertThat(new SlackWebhookFallbackFactory().create(cause)
                .post(java.net.URI.create("https://hooks.slack.com/x"), "{}")).isNull();
    }

    private ListAppender<ILoggingEvent> attachAppender(Class<?> type) {
        final Logger logger = (Logger) LoggerFactory.getLogger(type);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
