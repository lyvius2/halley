package banghak.home.halley.adapter.inbound.web.dto;

public record LoanEstimateResponse(
        Long propertyId,
        Long ltvLimit,
        Long dsrLimit,
        Long finalLimit,
        Long requiredCash,
        Long acquisitionTax,
        Long monthlyPayment
) {
}
