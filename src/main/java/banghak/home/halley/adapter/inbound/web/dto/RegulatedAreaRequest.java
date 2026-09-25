package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.loan.RegulationZone;

import java.time.LocalDate;

public record RegulatedAreaRequest(
        String codePrefix,
        RegulationZone zone,
        String areaName,
        LocalDate designatedOn,
        LocalDate releasedOn,
        String note
) {
}
