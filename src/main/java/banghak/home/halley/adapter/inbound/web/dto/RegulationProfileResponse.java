package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

public record RegulationProfileResponse(
        String activeProfile,
        List<String> profiles,
        List<RegulationParamResponse> params
) {
}
