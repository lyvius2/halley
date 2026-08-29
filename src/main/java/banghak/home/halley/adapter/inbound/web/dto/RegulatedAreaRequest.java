package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.loan.RegulationZone;

import java.time.LocalDate;

/**
 * 규제지역 지정 등록·수정 (설계 I68).
 *
 * @param codePrefix   법정동코드 앞자리. 5자리면 시군구 전체, 10자리면 그 법정동만
 * @param note         고시 번호 등 근거 — 왜 이 값이 들어왔는지 남겨야 나중에 검증할 수 있다
 */
public record RegulatedAreaRequest(
        String codePrefix,
        RegulationZone zone,
        String areaName,
        LocalDate designatedOn,
        LocalDate releasedOn,
        String note
) {
}
