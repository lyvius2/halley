package banghak.home.halley.domain.loan;

import java.util.List;

/**
 * 대출 산정 입력 (설계 I64).
 *
 * <p>인자가 여덟 개를 넘으면서 호출부에서 순서를 틀리기 쉬워졌습니다. 이름 있는 레코드로 묶습니다.
 *
 * @param askingPrice   호가(원) — 필요 자기자본·취득세의 기준
 * @param collateral    담보가치 추정 결과 — LTV의 기준. 호가와 다르다
 * @param annualIncome  연소득(원)
 * @param cash          보유 현금(원)
 * @param existingLoan  기존 대출 잔액(원). {@code existingDebts}가 비었을 때만 쓴다 —
 *                      종류를 모르면 주담대로 보고 계산한다
 * @param existingDebts 종류별 기존 부채 (설계 I92). <b>종류마다 DSR 산정만기가 달라</b>
 *                      한 덩어리로 보면 한도가 실제보다 크게 나온다
 * @param firstHome     생애최초 여부 — 취득세 감면에 쓴다
 * @param mortgageInsured MCI/MCG 가입 여부. 가입하면 방공제를 차감하지 않는다
 * @param rateType      금리유형 (설계 I97). 스트레스 가산폭을 가른다 — 고정금리는 붙지 않는다
 */
public record LoanEstimateInput(
        long askingPrice,
        CollateralValuation collateral,
        long annualIncome,
        long cash,
        long existingLoan,
        List<ExistingDebt> existingDebts,
        boolean firstHome,
        boolean mortgageInsured,
        RateType rateType
) {

    public LoanEstimateInput {
        existingDebts = existingDebts == null ? List.of() : List.copyOf(existingDebts);
        rateType = rateType == null ? RateType.VARIABLE : rateType;
    }

    /**
     * DSR에 잡히는 기존 부채의 연간 상환액 (설계 I92).
     *
     * <p>종류별 입력이 있으면 그것으로, 없으면 옛 단일 금액을 <b>주담대로 보고</b> 계산합니다.
     * 옛 값을 버리면 아직 종류를 입력하지 않은 사용자의 부채가 통째로 사라져 한도가
     * 부풀려집니다.
     */
    public long existingDebtAnnualPayment(double annualRate) {
        if (!existingDebts.isEmpty()) {
            return existingDebts.stream()
                    .mapToLong(debt -> debt.annualPayment(annualRate))
                    .sum();
        }
        return new ExistingDebt(DebtType.MORTGAGE, existingLoan).annualPayment(annualRate);
    }
}
