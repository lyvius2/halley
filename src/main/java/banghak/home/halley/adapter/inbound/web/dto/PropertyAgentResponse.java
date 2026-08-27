package banghak.home.halley.adapter.inbound.web.dto;

public record PropertyAgentResponse(
        Long agentId,
        String officeName,
        String agentName,
        String phone,
        String mobile,
        boolean isPrimary
) {
}
