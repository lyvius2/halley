package banghak.home.halley.adapter.inbound.web.dto;

/** 규제 파라미터 값 수정 (설계 I68). */
public record UpdateRegulationParamRequest(Long id, String paramValue) {
}
