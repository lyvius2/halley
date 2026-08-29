package banghak.home.halley.adapter.inbound.web.dto;

/**
 * 새 규제 프로파일 만들기 (설계 I68).
 *
 * @param profile      새 프로파일 이름. 고시일을 쓰면 이력이 읽힌다 (예: 2026-09-01)
 * @param copyFrom     복제할 원본 프로파일. 비우면 활성 프로파일에서 복제한다
 * @param activate     만들자마자 활성으로 전환할지
 */
public record CreateRegulationProfileRequest(String profile, String copyFrom, Boolean activate) {
}
