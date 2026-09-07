package banghak.home.halley.config;

import banghak.home.halley.application.service.RegulationNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 규제지역이 비어 있으면 국토부 고시에서 받아 채운다. */
@Slf4j
@Component
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
                log.error("Regulated area seeding aborted. cause={}", e.toString(), e);
            }
        });
    }
}
