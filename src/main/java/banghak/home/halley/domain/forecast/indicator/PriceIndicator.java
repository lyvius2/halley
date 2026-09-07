package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.PriceFactor;

import java.util.Optional;

/**
 * 가격 요인 하나를 계산한다.
 *
 * `CriterionScorer`와 같은 모양입니다 — 이 프로젝트가 이미 쓰는 패턴이라
 * 새로 배울 것이 없습니다.
 *
 * 코드가 계산합니다. LLM에게 산술을 시키지 않습니다. 중앙값·변동률을
 * 모델에게 맡기면 조용히 틀리고, 틀려도 그럴듯하게 씁니다.
 */
public interface PriceIndicator {

    String code();

    /**
     * @return 재료가 모자라면 empty. 비어 있는 것과 0은 다릅니다 —
     *         모르는 것을 0으로 두면 그 값이 계산에 섞입니다
     */
    Optional<PriceFactor> evaluate(ForecastInput input);
}
