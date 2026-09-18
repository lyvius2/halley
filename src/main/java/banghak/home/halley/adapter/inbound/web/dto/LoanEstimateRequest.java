package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.loan.RateType;

public record LoanEstimateRequest(
        Long annualIncome,
        Long cash,
        Long existingLoan,
        Boolean firstHome,
        Boolean mortgageInsured,
        Integer ownedHouseCount,
        RateType rateType
) {
}
