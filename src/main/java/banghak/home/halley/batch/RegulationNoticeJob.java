package banghak.home.halley.batch;

import banghak.home.halley.application.service.RegulationNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RegulationNoticeJob {

    private final RegulationNoticeService regulationNoticeService;

    public RegulationNoticeJob(RegulationNoticeService regulationNoticeService) {
        this.regulationNoticeService = regulationNoticeService;
    }

    /** 새벽 4시 — 고시는 업무시간에 나오므로 그날 것을 다음 새벽에 받는다.  */
    @Scheduled(cron = "0 0 4 * * *")
    public void refresh() {
        try {
            regulationNoticeService.refreshOutdated();
        } catch (RuntimeException e) {
            log.error("Regulation notice refresh failed. cause={}", e.toString(), e);
        }
    }
}
