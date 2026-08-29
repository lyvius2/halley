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

/**
 * 매물이 규제지역에 속하는지 판정한다 (설계 I66).
 *
 * <p>판정 키는 법정동코드입니다. `property.pnu`(I54)의 앞 10자리가 곧 법정동코드이므로
 * <b>외부를 부르지 않고</b> 씁니다. pnu가 없으면 지번주소로 시군구 코드를 역매핑합니다.
 *
 * <p>지정 정보가 없으면 {@link RegulationZone#NORMAL}입니다. 규제지역인데 등록을 안 해 둔 경우
 * 한도가 실제보다 높게 나오지만, 반대로 기본값을 규제로 두면 <b>전국이 규제지역이 되어</b>
 * 훨씬 크게 틀립니다.
 */
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
                // 같은 지역에 여러 지정이 겹치면 강한 쪽을 따른다
                .max(Comparator.comparingInt(area -> area.zone().ordinal()));
        matched.ifPresent(area -> log.debug("Regulated area matched. propertyId={}, zone={}, area={}",
                property.id(), area.zone(), area.areaName()));
        return matched.map(RegulatedArea::zone).orElse(RegulationZone.NORMAL);
    }

    /**
     * 법정동(10자리)과 시군구(5자리)를 모두 후보로 넣는다.
     * 고시는 시군구 단위가 많지만 일부 동만 지정되는 경우도 있어 둘 다 본다.
     */
    private List<String> codePrefixes(Property property) {
        final List<String> prefixes = new ArrayList<>();
        final String fromPnu = property.pnu() != null && property.pnu().length() >= 10
                ? property.pnu().substring(0, 10) : null;
        if (fromPnu != null) {
            prefixes.add(fromPnu);
            prefixes.add(fromPnu.substring(0, 5));
            return prefixes;
        }
        // pnu가 없으면 주소로 역매핑한다 (카카오를 부를 수 있어 pnu보다 뒤에 둔다)
        legalDongCodeService.deriveSigunguCode(property.addressJibun())
                .ifPresent(prefixes::add);
        return prefixes;
    }
}
