package banghak.home.halley.adapter.inbound.web.dto;

public record LoanEstimateRequest(
        Long annualIncome,
        Long cash,
        Boolean firstHome
) {
}
