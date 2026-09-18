package banghak.home.halley.config;

import banghak.home.halley.application.service.RegulationNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
// 시군구 사전(SigunguCodeBootstrap, @Order(10))이 채워진 뒤에 돈다
@Order(20)
public class RegulatedAreaBootstrap implements ApplicationRunner {

    private final RegulationNoticeService regulationNoticeService;

    public RegulatedAreaBootstrap(RegulationNoticeService regulationNoticeService) {
        this.regulationNoticeService = regulationNoticeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("regulated-area-seed").start(() -> {
            try {
                regulationNoticeService.seedIfEmpty();
            } catch (RuntimeException e) {
                // 여기서 새어 나가면 가상 스레드가 조용히 죽어 아무 기록도 남지 않는다
                log.error("Regulated area seeding aborted. cause={}", e.toString(), e);
            }
        });
    }
}
