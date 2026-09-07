package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

/** 규제 프로파일 현황. */
public record RegulationProfileResponse(
        String activeProfile,
        List<String> profiles,
        List<RegulationParamResponse> params
) {
}
