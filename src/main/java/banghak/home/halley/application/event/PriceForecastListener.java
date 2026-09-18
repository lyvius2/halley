package banghak.home.halley.application.event;

import banghak.home.halley.application.service.PriceForecastService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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
                // 여기서 새어 나가면 가상 스레드가 조용히 죽어 아무 기록도 남지 않는다
                log.error("Price forecast failed. propertyId={}, cause={}",
                        event.propertyId(), e.toString(), e);
            }
        });
    }
}
