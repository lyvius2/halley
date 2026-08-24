package banghak.home.halley.domain.loan;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

public record LoanEstimate(
        Long id,
        Long propertyId,
        ProductType productType,
        BigDecimal ltvRate,
        Long ltvLimit,
        Long dsrLimit,
        Long finalLimit,
        Long requiredCash,
        Long acquisitionTax,
        JsonNode assumptions,
        Instant computedAt
) {
}
