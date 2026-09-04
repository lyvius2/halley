package banghak.home.halley.config;

import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.application.service.GeoService;
import banghak.home.halley.domain.property.Property;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 이미 저장된 매물의 겹친 좌표를 건물 좌표로 바꾼다 (설계 I268).
 * 좌표가 같은 매물이 둘 이상인 경우에만 물어보므로, 한 번 갈라지면 다음부터는
 * 대상이 아니다 — 여러 번 돌려도 같다. 카카오 키가 없거나 막히면 그냥 안 바뀐다.
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
        return property.lat().setScale(6, RoundingMode.HALF_UP)
                + "," + property.lng().setScale(6, RoundingMode.HALF_UP);
    }
}
