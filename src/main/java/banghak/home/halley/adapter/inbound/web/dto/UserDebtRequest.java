package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.loan.DebtType;


public record UserDebtRequest(DebtType type, Long amount) {
}
