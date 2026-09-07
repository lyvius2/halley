package banghak.home.halley.adapter.inbound.web.dto;

/** 새 규제 프로파일 만들기. */
public record CreateRegulationProfileRequest(String profile, String copyFrom, Boolean activate) {
}
