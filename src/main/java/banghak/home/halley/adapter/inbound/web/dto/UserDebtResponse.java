package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.loan.DebtType;
import banghak.home.halley.domain.loan.ExistingDebt;

/**
 * 종류별 기존 부채 (설계 I92).
 *
 * @param dsrYears     DSR 산정만기(년). 왜 이 부담이 나오는지 화면에서 보여 준다
 * @param annualPayment 이 부채 때문에 매년 갚는 것으로 잡히는 금액
 */
public record UserDebtResponse(
        DebtType type,
        String typeLabel,
        long amount,
        int dsrYears,
        boolean interestOnly,
        long annualPayment
) {

    public static UserDebtResponse from(ExistingDebt debt, double annualRate) {
        return new UserDebtResponse(
                debt.type(), debt.type().label(), debt.amount(),
                debt.type().dsrYears(), debt.type().interestOnly(),
                debt.annualPayment(annualRate));
    }
}
