package banghak.home.halley.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러를 켜 둔다.
 *
 * <p>한때 매물 생존 확인 배치의 cron 을 `system_config` 에서 읽어 동적으로 걸었습니다.
 * <b>그 기능을 걷어냈습니다</b>(설계 I157) — 네이버 부동산은 매물이 완매돼도
 * URL 이 정상 응답을 주므로, HTTP 상태로는 판별할 수 없었습니다.
 *
 * <p>남은 잡들은 전부 {@code @Scheduled} 로 각자 cron 을 답니다.
 * 이 클래스는 그것들을 켜는 스위치만 합니다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
