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

/**
 * 전망을 다시 낸다 (설계 I143).
 *
 * <p><b>등록 때 한 번 내고 끝이었습니다.</b> 실거래는 매달 새로 들어오는데 화살표는
 * 등록 시점 그대로였습니다 — 반년 지난 매물의 화살표가 반년 전 시장을 가리킵니다.
 *
 * <p>사후 검증(구현 10)에도 이것이 필요합니다. 재계산이 없으면 이력에
 * <b>매물당 한 건</b>만 쌓여 견줄 과거가 생기지 않습니다.
 *
 * <p><b>매월 15일</b>에 돕니다. 국토부 신고 기한이 계약 후 30일이라 월초에는
 * 지난달 자료가 덜 찹니다(I129의 신고 지연과 같은 이유). 04:15 —
 * 규제지역(04:00)·공시금리(04:30)와 시간을 벌립니다.
 *
 * <p><b>대개 값이 쌉니다.</b> 60개월 중 새로 받을 달은 서너 달뿐이고(I128),
 * 지표가 그대로면 프롬프트 해시가 같아 LLM을 부르지 않습니다(I59).
 */
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

        // 게이트를 나눠 쓴다 — 매물 수만큼 한꺼번에 던지면 국토부 초당 제한에 걸린다 (설계 I140)
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

    /**
     * 판매완료·작성 중은 건너뛴다.
     *
     * <p>이미 팔린 매물의 전망은 <b>아무 판단에도 안 쓰입니다.</b> 그런데도 돌리면
     * 매달 국토부 호출만 늘어납니다.
     */
    private boolean worthRefreshing(Property property) {
        return !property.isDraft()
                && property.listingStatus() != banghak.home.halley.domain.property.ListingStatus.SOLD_OUT;
    }
}
