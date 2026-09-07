package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.loan.DebtType;
import banghak.home.halley.domain.loan.ExistingDebt;

/** 종류별 기존 부채. */
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
