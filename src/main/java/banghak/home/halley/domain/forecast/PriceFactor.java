package banghak.home.halley.domain.forecast;

/**
 * 가격에 작용하는 요인 하나 (설계 I130).
 *
 * @param evidence <b>근거는 반드시 있어야 합니다.</b> 근거 없는 요인은 화면에 띄우지 않습니다 —
 *                 이 프로젝트가 금리·LTV·스트레스 금리에서 지켜 온 규칙과 같습니다(I81·I116).
 *                 LLM이 지어낸 숫자를 거르는 기준이기도 합니다(2.2-A)
 */
public record PriceFactor(
        String name,
        ForecastDirection effect,
        FactorWeight weight,
        String evidence
) {
}
