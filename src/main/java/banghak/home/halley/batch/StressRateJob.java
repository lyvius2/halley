package banghak.home.halley.batch;

import banghak.home.halley.application.service.StressRateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 기준 스트레스 금리를 새로 산출한다 (설계 I116).
 *
 * <p><b>월 1회면 충분합니다.</b> 재료가 한국은행 월별 통계라 그보다 자주 봐야 할 이유가 없고,
 * 5년치를 한 번에 받는 호출이라 값이 쌉니다. 매월 1일 04:45 — 규제지역(04:00)·공시금리(04:30)와
 * 시간을 벌려 기동 직후 외부 호출이 몰리지 않게 합니다.
 *
 * <p>기동 때도 한 번 돕니다. 배포 직후 다음 1일까지 사람이 넣어 둔 값으로 계산하면
 * <b>규제와 다른 한도</b>가 나옵니다. 이미 최신이면 같은 값을 다시 쓸 뿐입니다.
 */
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

    /**
     * <b>여기서 던지면 애플리케이션이 뜨지 않습니다</b> — `ApplicationRunner`의 예외는
     * 기동 실패로 이어집니다. 가상 스레드로 비켜서 돌립니다: 5년치 조회가 기동을 붙잡을
     * 이유도 없습니다.
     */
    @Override
    @Order(30)
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("stress-rate-refresh").start(this::refresh);
    }
}
