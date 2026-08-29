package banghak.home.halley.domain.loan;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 규제지역 지정 한 건 (설계 I66).
 *
 * @param codePrefix   법정동코드 앞자리. 5자리면 시군구 전체, 10자리면 그 법정동만
 * @param designatedOn 고시 지정일
 * @param releasedOn   해제일. null이면 현재 유효하다
 * @param note         고시 번호 등 근거 — 왜 이 값이 들어왔는지 남지 않으면 나중에 검증할 수 없다
 */
public record RegulatedArea(
        Long id,
        String codePrefix,
        RegulationZone zone,
        String areaName,
        LocalDate designatedOn,
        LocalDate releasedOn,
        String note,
        Instant updatedAt
) {

    /** 기준일에 이 지정이 유효한지. */
    public boolean isActiveOn(LocalDate date) {
        if (designatedOn != null && date.isBefore(designatedOn)) {
            return false;
        }
        return releasedOn == null || date.isBefore(releasedOn);
    }
}
