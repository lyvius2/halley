package banghak.home.halley.application.event;

import banghak.home.halley.application.service.PriceForecastService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 보정이 끝났으니 가격 전망을 낸다 (설계 I126 · I135).
 *
 * <p><b>`@TransactionalEventListener`가 아닙니다.</b> 보정은 이미 커밋된 뒤 배경에서 도는
 * 작업이라 <b>묶일 트랜잭션이 없습니다.</b> `AFTER_COMMIT`을 걸면 트랜잭션 밖에서 발행된
 * 이벤트가 <b>조용히 버려집니다</b> — 예외도 로그도 없이 그냥 안 옵니다.
 *
 * <p>가상 스레드로 비켜서 돕니다. 60개월 조회와 LLM 판단에 1~2분이 걸리는데,
 * 그동안 보정 스레드를 붙잡을 이유가 없습니다.
 */
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
