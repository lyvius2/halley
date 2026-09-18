package banghak.home.halley.batch;

import banghak.home.halley.application.service.StressRateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StressRateJob implements ApplicationRunner {

    private final StressRateService stressRateService;

    public StressRateJob(StressRateService stressRateService) {
        this.stressRateService = stressRateService;
    }

    @Scheduled(cron = "0 45 4 1 * *")
    public void refresh() {
        try {
            stressRateService.refresh();
        } catch (RuntimeException e) {
            log.error("Stress rate refresh failed. cause={}", e.toString(), e);
        }
    }

    @Override
    @Order(30)
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("stress-rate-refresh").start(this::refresh);
    }
}
