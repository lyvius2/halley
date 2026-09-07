package banghak.home.halley.domain.regulation;

import banghak.home.halley.domain.loan.RegulationZone;

import java.time.LocalDate;
import java.util.List;

/** 국토교통부 규제지역 지정 고시 한 건. */
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
