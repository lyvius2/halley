package banghak.home.halley.batch;

import banghak.home.halley.application.service.MarketRateService;
import banghak.home.halley.domain.finance.LoanProductType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 공시 금리를 하루 한 번 새로 받는다. */
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
                log.error("Market rate refresh failed. type={}, cause={}", type, e.toString(), e);
            }
        }
    }
}
