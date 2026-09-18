package banghak.home.halley.batch;

import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.application.service.PriceForecastService;
import banghak.home.halley.config.VirtualThreadGate;
import banghak.home.halley.domain.property.Property;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Callable;

@Slf4j
@Component
public class PriceForecastJob {

    private final PriceForecastService priceForecastService;
    private final PropertyRepository propertyRepository;
    private final VirtualThreadGate gate;
    private final boolean enabled;

    public PriceForecastJob(PriceForecastService priceForecastService,
                            PropertyRepository propertyRepository,
                            @Qualifier("forecastGate") VirtualThreadGate gate,
                            @Value("${forecast.monthly-refresh-enabled:true}") boolean enabled) {
        this.priceForecastService = priceForecastService;
        this.propertyRepository = propertyRepository;
        this.gate = gate;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${forecast.monthly-refresh-cron:0 15 4 15 * *}")
    public void refreshAll() {
        if (!enabled) {
            log.info("Monthly forecast refresh is disabled.");
            return;
        }
        final List<Property> targets = propertyRepository.findAll().stream()
                .filter(this::worthRefreshing)
                .toList();
        if (targets.isEmpty()) {
            log.info("Monthly forecast refresh - nothing to do.");
            return;
        }
        log.info("Monthly forecast refresh started. properties={}", targets.size());
        final long startedAt = System.currentTimeMillis();

        // 게이트를 나눠 쓴다 — 매물 수만큼 한꺼번에 던지면 국토부 초당 제한에 걸린다
        final List<Callable<Long>> tasks = targets.stream()
                .map(p -> (Callable<Long>) () -> {
                    // 한 매물이 터져도 나머지는 돈다. 여기서 새어 나가면 그 자리만 null 이 된다
                    priceForecastService.refresh(p.id());
                    return p.id();
                })
                .toList();
        final long done = gate.runAll(tasks).stream().filter(java.util.Objects::nonNull).count();

        log.info("Monthly forecast refresh finished. ok={}, failed={}, elapsedMs={}",
                done, targets.size() - done, System.currentTimeMillis() - startedAt);
    }

    private boolean worthRefreshing(Property property) {
        return !property.isDraft()
                && property.listingStatus() != banghak.home.halley.domain.property.ListingStatus.SOLD_OUT;
    }
}
