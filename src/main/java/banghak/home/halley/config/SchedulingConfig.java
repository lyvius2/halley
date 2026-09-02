package banghak.home.halley.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;

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

    /**
     * 커넥션 풀을 들여다볼 통로 (설계 I243).
     *
     * <p>{@code DataSource} 를 {@link ConnectionPoolWatch} 가 직접 받으면 생성자가
     * 둘이 되어(테스트용 하나 더) 어느 쪽으로 만들지 못 정합니다. 통로를 여기서
     * 만들어 넘깁니다.
     */
    @Bean
    ConnectionPoolWatch.PoolProbe poolProbe(DataSource dataSource) {
        return ConnectionPoolWatch.hikariProbe(dataSource);
    }

    /**
     * 스케줄러 스레드를 <b>둘</b>로 (설계 I243).
     *
     * <p>기본값이 하나입니다. 잡이 여섯인데 하나로 돌면 <b>먼저 잡은 것이 끝날 때까지
     * 나머지가 밀립니다</b> — 전망 배치처럼 오래 도는 것이 있어 실제로 밀립니다.
     *
     * <p>감시는 여기 안 태웠습니다({@link ConnectionPoolWatch} 는 제 스레드를 씁니다).
     * 그래도 둘로 둡니다 — 새벽 4시에 잡 셋이 몰려 있습니다.
     */
    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("halley-sched-");
        // 내리는 중에 잡이 걸려 있으면 기다린다 — 반쯤 쓴 상태로 끊기지 않게
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }
}
