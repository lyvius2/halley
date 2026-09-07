package banghak.home.halley.batch;

import banghak.home.halley.application.service.StressRateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 기준 스트레스 금리를 새로 산출한다. */
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

 /** 여기서 던지면 애플리케이션이 뜨지 않습니다. ApplicationRunner의 예외는 */
    @Override
    @Order(30)
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("stress-rate-refresh").start(this::refresh);
    }
}
