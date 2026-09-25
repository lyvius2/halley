package banghak.home.halley.domain.regulation;

import banghak.home.halley.domain.loan.RegulationZone;

import java.time.LocalDate;
import java.util.List;

public record RegulationNotice(
        RegulationZone zone,
        String noticeNo,
        LocalDate announcedOn,
        List<String> areaNames
) {

    public boolean hasAreas() {
        return areaNames != null && !areaNames.isEmpty();
    }
}
