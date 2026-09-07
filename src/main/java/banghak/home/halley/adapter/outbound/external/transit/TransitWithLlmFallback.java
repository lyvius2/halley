package banghak.home.halley.adapter.outbound.external.transit;

import banghak.home.halley.adapter.outbound.external.odsay.OdsayTransitAdapter;
import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.config.exception.TransitQuotaExceededException;
import banghak.home.halley.domain.itinerary.RoutePath;
import banghak.home.halley.domain.scoring.TransitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import banghak.home.halley.config.VirtualThreadGate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** ODsay 를 먼저, 하루치를 다 썼으면 LLM 으로. */
@Slf4j
@Component
public class TransitWithLlmFallback implements OdsayTransitPort {

    private final OdsayTransitAdapter odsay;
    private final LlmTransitEstimator estimator;

 /** 하루치를 다 쓴 날. */
    private volatile LocalDate exhaustedOn;

 /** 구간 하나만 물을 때의 열쇠. 프롬프트와 로그에 그대로 실리므로 읽히는 이름을 쓴다. */
    private static final String SINGLE = "leg";

 /** 구간들을 한꺼번에 물을 때 쓰는 자리. */
    private final VirtualThreadGate gate;
 /** 이 시간 안에 못 받은 구간은 추정으로 넘깁니다. */
    private final Duration batchBudget;

    public TransitWithLlmFallback(OdsayTransitAdapter odsay, LlmTransitEstimator estimator,
                                  @Qualifier("itineraryGate") VirtualThreadGate gate,
                                  @Value("${itinerary.transit-budget-seconds:20}") long budgetSeconds) {
        this.odsay = odsay;
        this.estimator = estimator;
        this.gate = gate;
        this.batchBudget = Duration.ofSeconds(budgetSeconds);
    }

 /** ODsay 가 막혀도 LLM 이 있으면 산출할 수 있습니다. */
    @Override
    public boolean isEnabled() {
        return odsay.isEnabled() || estimator.isEnabled();
    }

    @Override
    public TransitResult findTransit(double startX, double startY, double endX, double endY) {
        if (!quotaExhausted()) {
            try {
                return odsay.findTransit(startX, startY, endX, endY);
            } catch (TransitQuotaExceededException e) {
                markExhausted(e);
            }
        }
        final Map<String, TransitResult> estimated = estimator.estimate(
                List.of(new LlmTransitEstimator.Leg(SINGLE, startX, startY, endX, endY)));
        return estimated.getOrDefault(SINGLE, TransitResult.missing());
    }

 /** 여러 구간을 한꺼번에. */
    @Override
    public Map<String, TransitResult> findTransitBatch(Map<String, double[]> legs) {
        final Map<String, TransitResult> found = new ConcurrentHashMap<>();
        gate.runWithin(legs.entrySet().stream()
                .map(entry -> (Runnable) () -> {
                    if (quotaExhausted()) {
                        return;
                    }
                    final double[] c = entry.getValue();
                    try {
                        found.put(entry.getKey(), odsay.findTransit(c[0], c[1], c[2], c[3]));
                    } catch (TransitQuotaExceededException e) {
                        markExhausted(e);
                    }
                })
                .toList(), batchBudget);

        final List<LlmTransitEstimator.Leg> unresolved = legs.entrySet().stream()
                .filter(entry -> !found.containsKey(entry.getKey()))
                .map(entry -> new LlmTransitEstimator.Leg(entry.getKey(),
                        entry.getValue()[0], entry.getValue()[1],
                        entry.getValue()[2], entry.getValue()[3]))
                .toList();
        if (!unresolved.isEmpty()) {
            log.info("Transit legs unresolved by ODsay - estimating. total={}, unresolved={}",
                    legs.size(), unresolved.size());
        }
        found.putAll(estimator.estimate(unresolved));
        return found;
    }

 /** 경로선은 ODsay 만 줍니다. 없으면 화면이 직선으로 그립니다. */
    @Override
    public RoutePath findLane(String mapObj) {
        if (quotaExhausted()) {
            return RoutePath.empty();
        }
        try {
            return odsay.findLane(mapObj);
        } catch (TransitQuotaExceededException e) {
            markExhausted(e);
            return RoutePath.empty();
        }
    }

 /** 이번 답이 ODsay 가 아니라 추정인지. 저장하는 쪽이 출처를 남길 때 본다. */
    public boolean estimating() {
        return quotaExhausted();
    }

    private boolean quotaExhausted() {
        return LocalDate.now().equals(exhaustedOn);
    }

    private void markExhausted(TransitQuotaExceededException e) {
        final LocalDate today = LocalDate.now();
        if (!today.equals(exhaustedOn)) {
            log.warn("ODsay quota is spent for today - estimating with the LLM instead. cause={}", e.getMessage());
        }
        exhaustedOn = today;
    }
}
