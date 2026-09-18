package banghak.home.halley.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    ConnectionPoolWatch.PoolProbe poolProbe(DataSource dataSource) {
        return ConnectionPoolWatch.hikariProbe(dataSource);
    }

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
