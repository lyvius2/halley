package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.RegulatedAreaRepository;
import banghak.home.halley.domain.loan.RegulatedArea;
import banghak.home.halley.domain.loan.RegulationZone;
import banghak.home.halley.domain.property.Property;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 매물이 규제지역에 속하는지 판정한다. */
@Slf4j
@Service
public class RegulatedAreaService {

    private final RegulatedAreaRepository regulatedAreaRepository;
    private final LegalDongCodeService legalDongCodeService;

    public RegulatedAreaService(RegulatedAreaRepository regulatedAreaRepository,
                                LegalDongCodeService legalDongCodeService) {
        this.regulatedAreaRepository = regulatedAreaRepository;
        this.legalDongCodeService = legalDongCodeService;
    }

    public RegulationZone resolve(Property property) {
        return resolve(property, LocalDate.now());
    }

    RegulationZone resolve(Property property, LocalDate on) {
        final List<String> prefixes = codePrefixes(property);
        if (prefixes.isEmpty()) {
            return RegulationZone.NORMAL;
        }
        final Optional<RegulatedArea> matched = regulatedAreaRepository.findByCodePrefixes(prefixes).stream()
                .filter(area -> area.isActiveOn(on))
                .max(Comparator.comparingInt(area -> area.zone().ordinal()));
        matched.ifPresent(area -> log.debug("Regulated area matched. propertyId={}, zone={}, area={}",
                property.id(), area.zone(), area.areaName()));
        return matched.map(RegulatedArea::zone).orElse(RegulationZone.NORMAL);
    }

 /** 법정동(10자리)과 시군구(5자리)를 모두 후보로 넣는다. */
    private List<String> codePrefixes(Property property) {
        final List<String> prefixes = new ArrayList<>();
        final String fromPnu = property.pnu() != null && property.pnu().length() >= 10
                ? property.pnu().substring(0, 10) : null;
        if (fromPnu != null) {
            prefixes.add(fromPnu);
            prefixes.add(fromPnu.substring(0, 5));
            return prefixes;
        }
        legalDongCodeService.deriveSigunguCode(property.addressJibun())
                .ifPresent(prefixes::add);
        return prefixes;
    }
}
