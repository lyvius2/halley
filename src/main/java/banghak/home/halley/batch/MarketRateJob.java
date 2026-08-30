package banghak.home.halley.batch;

import banghak.home.halley.application.service.MarketRateService;
import banghak.home.halley.domain.finance.LoanProductType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공시 금리를 하루 한 번 새로 받는다 (설계 I81).
 *
 * <p>공시는 월 단위로 바뀌므로 자주 볼 필요가 없습니다. <b>일 허용횟수가 있는 API</b>라
 * (`err_cd = 020`) 대출 계산마다 부르면 금방 한도에 걸립니다.
 *
 * <p>규제지역 갱신(04:00)과 시간을 벌려 둡니다 — 기동 직후 외부 호출이 몰리지 않게 합니다.
 */
@Slf4j
@Component
public class MarketRateJob {

    private final MarketRateService marketRateService;

    public MarketRateJob(MarketRateService marketRateService) {
        this.marketRateService = marketRateService;
    }

    @Scheduled(cron = "0 30 4 * * *")
    public void refresh() {
        for (final LoanProductType type : LoanProductType.values()) {
            try {
                marketRateService.refresh(type);
            } catch (RuntimeException e) {
                // 한 상품이 실패해도 다른 상품은 받아야 한다
                log.error("Market rate refresh failed. type={}, cause={}", type, e.toString(), e);
            }
        }
    }
}
