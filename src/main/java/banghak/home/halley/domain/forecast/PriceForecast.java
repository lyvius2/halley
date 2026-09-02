package banghak.home.halley.domain.forecast;

import java.time.Instant;

/**
 * 저장된 가격 전망 (설계 I135).
 *
 * @param llmDirection  LLM 이 <b>스스로 낸</b> 결론 (설계 I249). {@code outlook.direction()} 은
 *                      규칙까지 거친 <b>최종</b> 결론이라 둘이 다를 수 있습니다 —
 *                      LLM 이 판단을 보류해 우리가 지표를 세어 넣었으면 여기는 {@code UNCERTAIN}
 *                      입니다. 누가 무엇을 말했는지 남겨야 <b>'유력'을 가릴 수 있고</b>
 *                      사후 검증(구현 10)에서 <b>누가 맞혔는지 셀 수 있습니다.</b>
 *                      옛 행은 {@code null} 입니다 — "모른다"는 뜻이라 유력이 안 붙습니다
 * @param codeDirection 코드 예측. <b>결론이 아니라 참고 문구용</b>입니다 (설계 5.2).
 *                      나중에 실제 결과와 맞춰 어느 쪽이 더 맞았는지 보려면 이 값이 필요합니다
 * @param promptHash    같은 지표면 다시 묻지 않기 위한 값 (설계 I59)
 * @param model         어느 모델이 판단했는지. LLM 없이 코드만으로 냈으면 null
 */
public record PriceForecast(
        Long id,
        Long propertyId,
        PriceOutlook outlook,
        ForecastDirection llmDirection,
        ForecastDirection codeDirection,
        String promptHash,
        String model,
        Instant computedAt
) {

    /** 두 예측이 같은 방향인가 — 모달의 참고 문구를 가른다. */
    public boolean agreed() {
        return outlook.direction() == codeDirection;
    }

    /**
     * <b>유력</b>한가 (설계 I249).
     *
     * <p>LLM 이 <b>방향을 말했고</b>(상승·하락), <b>지표를 세어도 같은 쪽</b>이면
     * 유력입니다. 두 갈래가 따로 같은 결론에 닿은 것이라 더 믿을 만합니다.
     *
     * <p>LLM 이 판단을 보류해 우리가 세어 넣은 경우는 <b>유력이 아닙니다.</b>
     * 견줄 상대가 없었으니까요 — 그때 {@code direction} 과 지표 판정은 <b>같을 수밖에
     * 없습니다.</b> 그것을 "일치"로 읽으면 <b>늘 유력</b>이 됩니다.
     */
    public boolean strong() {
        if (llmDirection != ForecastDirection.UP && llmDirection != ForecastDirection.DOWN) {
            return false;
        }
        return FactorTally.of(outlook.factors()).direction() == llmDirection;
    }
}
