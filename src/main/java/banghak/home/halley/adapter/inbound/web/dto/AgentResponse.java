package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;

public record AgentResponse(
        Long id,
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
