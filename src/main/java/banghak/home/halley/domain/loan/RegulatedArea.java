package banghak.home.halley.domain.loan;

import java.time.Instant;
import java.time.LocalDate;

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

    /** 기준일에 이 지정이 유효한지.  */
    public boolean isActiveOn(LocalDate date) {
        if (designatedOn != null && date.isBefore(designatedOn)) {
            return false;
        }
        return releasedOn == null || date.isBefore(releasedOn);
    }
}
