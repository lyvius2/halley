package banghak.home.halley.application.event;

import banghak.home.halley.application.service.PriceForecastService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 보정이 끝났으니 가격 전망을 낸다. */
@Slf4j
@Component
public class PriceForecastListener {

    private final PriceForecastService priceForecastService;

    public PriceForecastListener(PriceForecastService priceForecastService) {
        this.priceForecastService = priceForecastService;
    }

    @EventListener
    public void onEnriched(PropertyEnrichedEvent event) {
        Thread.ofVirtual().name("forecast-" + event.propertyId()).start(() -> {
            try {
                priceForecastService.refresh(event.propertyId());
            } catch (RuntimeException e) {
                log.error("Price forecast failed. propertyId={}, cause={}",
                        event.propertyId(), e.toString(), e);
            }
        });
    }
}
