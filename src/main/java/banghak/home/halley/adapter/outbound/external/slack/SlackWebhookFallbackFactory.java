package banghak.home.halley.adapter.outbound.external.slack;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import static banghak.home.halley.adapter.outbound.external.FallbackCause.describe;

@Slf4j
@Component
public class SlackWebhookFallbackFactory implements FallbackFactory<SlackWebhookClient> {

    @Override
    public SlackWebhookClient create(Throwable cause) {
        return (payload, contentType) -> {
            log.warn("Slack 웹훅 발송 실패 — 폴백 반환(NOTIFICATION_LOG에 FAILED로 기록됨). cause={}", describe(cause));
            return null;
        };
    }
}
