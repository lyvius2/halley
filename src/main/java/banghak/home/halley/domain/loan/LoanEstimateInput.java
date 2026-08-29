package banghak.home.halley.domain.loan;

/**
 * 대출 산정 입력 (설계 I64).
 *
 * <p>인자가 여덟 개를 넘으면서 호출부에서 순서를 틀리기 쉬워졌습니다. 이름 있는 레코드로 묶습니다.
 *
 * @param askingPrice   호가(원) — 필요 자기자본·취득세의 기준
 * @param collateral    담보가치 추정 결과 — LTV의 기준. 호가와 다르다
 * @param annualIncome  연소득(원)
 * @param cash          보유 현금(원)
 * @param existingLoan  기존 대출 잔액(원)
 * @param firstHome     생애최초 여부 — 취득세 감면에 쓴다
 * @param mortgageInsured MCI/MCG 가입 여부. 가입하면 방공제를 차감하지 않는다
 */
public record LoanEstimateInput(
        long askingPrice,
        CollateralValuation collateral,
        long annualIncome,
        long cash,
        long existingLoan,
        boolean firstHome,
        boolean mortgageInsured
) {
}
