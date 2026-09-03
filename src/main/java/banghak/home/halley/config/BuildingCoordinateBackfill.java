package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.application.service.GeoService;
import banghak.home.halley.domain.property.Property;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 이미 저장된 매물의 <b>겹친 좌표</b>를 건물 좌표로 바꾼다 (설계 I268).
 *
 * <p>주소검색이 동을 무시했으므로, 같은 단지 매물은 <b>정확히 같은 좌표</b>로
 * 저장되어 있습니다. 새로 등록하는 것만 고치면 <b>이미 넣어 둔 것은 영영</b>
 * 포개진 채로 남습니다.
 *
 * <h4>겹친 것만 본다</h4>
 *
 * <p>좌표가 같은 매물이 <b>둘 이상</b>인 경우에만 물어봅니다. 그래서
 * <b>여러 번 돌려도 같습니다</b> — 한 번 갈라지면 다음부터는 대상이 아닙니다.
 * 별도의 표시 열이 필요 없습니다.
 *
 * <p>카카오 키가 없거나 조회가 막히면 {@code GeoService} 가 빈 값을 돌려주므로
 * <b>그냥 아무것도 안 바뀝니다.</b> 기동은 막지 않습니다.
 */
@Slf4j
@Component
@Order(60)
public class BuildingCoordinateBackfill implements ApplicationRunner {

    private final PropertyRepository propertyRepository;
    private final GeoService geoService;

    public BuildingCoordinateBackfill(PropertyRepository propertyRepository,
                                      GeoService geoService) {
        this.propertyRepository = propertyRepository;
        this.geoService = geoService;
    }

    @Override
    public void run(ApplicationArguments args) {
        final Map<String, List<Property>> bySpot = new LinkedHashMap<>();
        for (final Property property : propertyRepository.findAll()) {
            if (property.lat() == null || property.lng() == null || property.dongHo() == null) {
                continue;
            }
            bySpot.computeIfAbsent(spot(property), key -> new ArrayList<>()).add(property);
        }

        int moved = 0;
        for (final List<Property> sharing : bySpot.values()) {
            if (sharing.size() < 2) {
                continue;
            }
            for (final Property property : sharing) {
                if (refine(property)) {
                    moved++;
                }
            }
        }
        if (moved > 0) {
            log.info("Moved {} properties to their own building coordinate (설계 I268).", moved);
        }
    }

    private boolean refine(Property property) {
        return geoService.geocodeBuilding(property.name(), property.dongHo(),
                        property.lat(), property.lng())
                .map(found -> {
                    propertyRepository.setCoordinates(property.id(), found.lat(), found.lng());
                    log.info("Building coordinate found. propertyId={}, dongHo={}, place={}",
                            property.id(), property.dongHo(), found.addressName());
                    return true;
                })
                .orElse(false);
    }

    /** 소수점 여섯 자리면 1m 안쪽 — 같은 자리로 본다. */
    private static String spot(Property property) {
        return property.lat().setScale(6, java.math.RoundingMode.HALF_UP)
                + "," + property.lng().setScale(6, java.math.RoundingMode.HALF_UP);
    }
}
