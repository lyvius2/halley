package banghak.home.halley.adapter.inbound.web.dto;

import java.time.Instant;

public record LoanEstimateHistoryResponse(
        Long propertyId,
        Long ltvLimit,
        Long dsrLimit,
        Long finalLimit,
        Long requiredCash,
        Long acquisitionTax,
        Instant computedAt
) {
}
