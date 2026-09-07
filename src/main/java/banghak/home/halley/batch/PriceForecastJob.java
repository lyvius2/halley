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

/** 전망을 다시 낸다. */
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

        final List<Callable<Long>> tasks = targets.stream()
                .map(p -> (Callable<Long>) () -> {
                    priceForecastService.refresh(p.id());
                    return p.id();
                })
                .toList();
        final long done = gate.runAll(tasks).stream().filter(java.util.Objects::nonNull).count();

        log.info("Monthly forecast refresh finished. ok={}, failed={}, elapsedMs={}",
                done, targets.size() - done, System.currentTimeMillis() - startedAt);
    }

 /** 판매완료·작성 중은 건너뛴다. */
    private boolean worthRefreshing(Property property) {
        return !property.isDraft()
                && property.listingStatus() != banghak.home.halley.domain.property.ListingStatus.SOLD_OUT;
    }
}
