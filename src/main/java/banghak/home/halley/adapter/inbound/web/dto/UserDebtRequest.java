package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.loan.DebtType;

/** @param amount 잔액. 마이너스통장은 쓴 금액이 아니라 한도를 넣는다 */
public record UserDebtRequest(DebtType type, Long amount) {
}
