package banghak.home.halley.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;

/** 스케줄러를 켜 둔다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {

 /** 커넥션 풀을 들여다볼 통로. */
    @Bean
    ConnectionPoolWatch.PoolProbe poolProbe(DataSource dataSource) {
        return ConnectionPoolWatch.hikariProbe(dataSource);
    }

 /** 스케줄러 스레드를 둘로. */
    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("halley-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }
}
