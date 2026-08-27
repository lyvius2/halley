package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;

public record AgentRequest(
        String officeName,
        String agentName,
        String phone,
        String mobile,
        String registrationNo,
        String address,
        BigDecimal lat,
        BigDecimal lng
) {
}
