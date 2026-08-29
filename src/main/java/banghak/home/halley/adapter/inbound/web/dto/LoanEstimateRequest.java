package banghak.home.halley.adapter.inbound.web.dto;

/**
 * 대출 한도 계산 입력 (설계 3.4 · I55).
 *
 * <p>`mortgageInsured`는 MCI/MCG 가입 여부입니다. 가입하면 방공제를 차감하지 않습니다(설계 I64-3).
 *
 * <p>세 금액은 모두 <b>비워 보낼 수 있습니다</b>. 비면 서버가 로그인 사용자의 프로필
 * (연소득 · 보유 현금 · 기존 대출액)에서 채웁니다 — 모달을 열자마자 결과가 보이도록 하기 위한 것입니다.
 */
public record LoanEstimateRequest(
        Long annualIncome,
        Long cash,
        Long existingLoan,
        Boolean firstHome,
        Boolean mortgageInsured
) {
}
