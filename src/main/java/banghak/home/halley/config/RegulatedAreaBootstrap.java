package banghak.home.halley.config;

import banghak.home.halley.application.service.RegulationNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 규제지역이 비어 있으면 국토부 고시에서 받아 채운다 (설계 I73).
 *
 * <p><b>가상 스레드로 비켜서 돌립니다.</b> 법제처 조회 세 번 + PDF 파싱 + LLM 호출이라 몇 초가
 * 걸리는데, 기동을 그만큼 붙잡아 둘 이유가 없습니다. 적재가 끝나기 전에 대출 계산이 들어오면
 * {@code RegulationSeedStatus}가 아직 {@code RUNNING}이므로 화면에 경고가 붙습니다.
 */
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
