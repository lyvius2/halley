package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.loan.RegulatedArea;
import banghak.home.halley.domain.loan.RegulationZone;

import java.time.Instant;
import java.time.LocalDate;

/** 규제지역 지정 한 건. */
public record RegulatedAreaResponse(
        Long id,
        String codePrefix,
        RegulationZone zone,
        String zoneLabel,
        String areaName,
        LocalDate designatedOn,
        LocalDate releasedOn,
        String note,
        boolean active,
        Instant updatedAt
) {

    public static RegulatedAreaResponse from(RegulatedArea a, LocalDate today) {
        return new RegulatedAreaResponse(
                a.id(), a.codePrefix(), a.zone(), a.zone().label(), a.areaName(),
                a.designatedOn(), a.releasedOn(), a.note(), a.isActiveOn(today), a.updatedAt());
    }
}
