package banghak.home.halley.domain.forecast;

import java.time.Instant;

/**
 * 저장된 가격 전망 (설계 I135).
 *
 * @param codeDirection 코드 예측. <b>결론이 아니라 참고 문구용</b>입니다 (설계 5.2).
 *                      나중에 실제 결과와 맞춰 어느 쪽이 더 맞았는지 보려면 이 값이 필요합니다
 * @param promptHash    같은 지표면 다시 묻지 않기 위한 값 (설계 I59)
 * @param model         어느 모델이 판단했는지. LLM 없이 코드만으로 냈으면 null
 */
public record PriceForecast(
        Long id,
        Long propertyId,
        PriceOutlook outlook,
        ForecastDirection codeDirection,
        String promptHash,
        String model,
        Instant computedAt
) {

    /** 두 예측이 같은 방향인가 — 모달의 참고 문구를 가른다. */
    public boolean agreed() {
        return outlook.direction() == codeDirection;
    }
}
