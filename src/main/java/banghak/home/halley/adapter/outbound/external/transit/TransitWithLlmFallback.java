package banghak.home.halley.adapter.outbound.external.transit;

import banghak.home.halley.adapter.outbound.external.odsay.OdsayTransitAdapter;
import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.config.exception.TransitQuotaExceededException;
import banghak.home.halley.domain.itinerary.RoutePath;
import banghak.home.halley.domain.scoring.TransitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ODsay 를 먼저, 하루치를 다 썼으면 LLM 으로 (설계 I210).
 *
 * <p>실제 운영 로그에서 <b>`code=429, msg=Daily quota exceeded`</b> 가 줄줄이 났습니다.
 * 그때 직주근접은 통째로 미산출이 되고, 임장 대중교통은 모든 구간이 999분이 됩니다 —
 * <b>화면이 죽은 것처럼 보입니다.</b>
 *
 * <p><b>추정이라는 사실은 숨기지 않습니다.</b> LLM 은 시간표를 조회하지 않고 아는 것으로
 * 말합니다. 부르는 쪽이 그 차이를 알 수 있게 `mapObj` 를 비워 돌려주고,
 * 저장하는 쪽은 출처를 남깁니다.
 *
 * <p><b>경로선은 LLM 에게 묻지 않습니다.</b> 좌표를 지어내게 하면 <b>있지도 않은 길</b>이
 * 지도에 그려집니다. 그건 없는 것보다 나쁩니다 — 직선으로 그립니다(현행 그대로).
 *
 * <p><b>`@Primary` 를 붙이지 않았습니다.</b> `OdsayTransitAdapter` 가 포트를 구현하지
 * 않으므로 운영에서는 이것이 유일한 포트 빈입니다. 붙이면 <b>테스트가 갈아 끼우는
 * 대역과 부딪힙니다</b> — 우선 빈이 둘이 되어 스프링이 고르지 못합니다.
 */
@Slf4j
@Component
public class TransitWithLlmFallback implements OdsayTransitPort {

    private final OdsayTransitAdapter odsay;
    private final LlmTransitEstimator estimator;

    /**
     * 하루치를 다 쓴 날 (설계 I210).
     *
     * <p>한 번 429 를 보면 <b>그날은 더 부르지 않습니다.</b> 안 그러면 매물 하나 채점할
     * 때마다 사람 수만큼 429 를 받으러 갑니다 — 로그만 더러워지고 얻는 것이 없습니다.
     *
     * <p>날짜로 둡니다. 할당량이 <b>하루 단위</b>라 자정을 넘기면 저절로 풀립니다 —
     * 타이머를 두면 서버를 다시 띄울 때 잃습니다.
     */
    private volatile LocalDate exhaustedOn;

    /** 구간 하나만 물을 때의 열쇠. 프롬프트와 로그에 그대로 실리므로 읽히는 이름을 쓴다. */
    private static final String SINGLE = "leg";

    public TransitWithLlmFallback(OdsayTransitAdapter odsay, LlmTransitEstimator estimator) {
        this.odsay = odsay;
        this.estimator = estimator;
    }

    /**
     * ODsay 가 막혀도 LLM 이 있으면 <b>산출할 수 있습니다.</b>
     *
     * <p>이 값이 거짓이면 부르는 쪽이 "미산출" 이유로 삼습니다(설계 I119).
     * 둘 다 없을 때만 거짓입니다.
     */
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

    /**
     * 여러 구간을 한꺼번에 (설계 I210).
     *
     * <p>임장 행렬은 매물 8개면 <b>72쌍</b>입니다. 쌍마다 LLM 을 부르면 한 번 계산에
     * 수십 분이 걸립니다 — ODsay 라면 쌍마다 불러도 괜찮지만(50ms) LLM 은 아닙니다.
     *
     * <p>그래서 <b>ODsay 는 돌면서</b>, LLM 은 <b>남은 것을 한 번에</b> 묻습니다.
     */
    @Override
    public Map<String, TransitResult> findTransitBatch(Map<String, double[]> legs) {
        final Map<String, TransitResult> found = new LinkedHashMap<>();
        final List<LlmTransitEstimator.Leg> unresolved = new java.util.ArrayList<>();
        for (final Map.Entry<String, double[]> entry : legs.entrySet()) {
            final double[] c = entry.getValue();
            if (quotaExhausted()) {
                unresolved.add(new LlmTransitEstimator.Leg(entry.getKey(), c[0], c[1], c[2], c[3]));
                continue;
            }
            try {
                found.put(entry.getKey(), odsay.findTransit(c[0], c[1], c[2], c[3]));
            } catch (TransitQuotaExceededException e) {
                markExhausted(e);
                unresolved.add(new LlmTransitEstimator.Leg(entry.getKey(), c[0], c[1], c[2], c[3]));
            }
        }
        found.putAll(estimator.estimate(unresolved));
        return found;
    }

    /** 경로선은 ODsay 만 줍니다. 없으면 화면이 직선으로 그립니다 (설계 I177 · I210). */
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

    /** 이번 답이 ODsay 가 아니라 추정인지 — 저장하는 쪽이 출처를 남길 때 본다. */
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
