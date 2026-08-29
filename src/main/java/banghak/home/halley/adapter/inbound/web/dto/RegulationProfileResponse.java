package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

/**
 * 규제 프로파일 현황 (설계 I68).
 *
 * @param activeProfile 지금 대출 계산에 쓰이는 프로파일
 * @param profiles      등록된 프로파일 이름 목록
 * @param params        활성 프로파일의 파라미터 (키순)
 */
public record RegulationProfileResponse(
        String activeProfile,
        List<String> profiles,
        List<RegulationParamResponse> params
) {
}
