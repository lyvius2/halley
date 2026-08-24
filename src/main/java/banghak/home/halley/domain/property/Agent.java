package banghak.home.halley.domain.property;

import java.math.BigDecimal;

public record Agent(
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
