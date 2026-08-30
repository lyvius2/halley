package banghak.home.halley.batch;

import banghak.home.halley.application.service.RegulationNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 규제지역 고시가 갱신됐는지 하루 한 번 확인한다 (설계 I73).
 *
 * <p>고시는 연 1~2회 바뀌므로 자주 볼 필요가 없습니다. 다만 <b>바뀐 것을 모르는 채로 옛 값을
 * 쓰는 것</b>이 이 배치가 막으려는 상황이고, 그건 하루 단위면 충분합니다.
 */
@Slf4j
@Component
public class RegulationNoticeJob {

    private final RegulationNoticeService regulationNoticeService;

    public RegulationNoticeJob(RegulationNoticeService regulationNoticeService) {
        this.regulationNoticeService = regulationNoticeService;
    }

    /** 새벽 4시 — 고시는 업무시간에 나오므로 그날 것을 다음 새벽에 받는다. */
    @Scheduled(cron = "0 0 4 * * *")
    public void refresh() {
        try {
            regulationNoticeService.refreshOutdated();
        } catch (RuntimeException e) {
            log.error("Regulation notice refresh failed. cause={}", e.toString(), e);
        }
    }
}
