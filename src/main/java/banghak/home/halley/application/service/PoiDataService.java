package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.adapter.outbound.persistence.NearbyFacilityRepository;
import banghak.home.halley.domain.geo.PoiResult;
import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.property.Property;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PoiDataService {

    private static final List<CategorySpec> CATEGORIES = List.of(
            new CategorySpec("STATION", "SW8", 2000),
            new CategorySpec("EDUCATION", "SC4", 2000),
            new CategorySpec("EDUCATION", "PS3", 2000),
            new CategorySpec("AMENITY", "CS2", 1300),
            new CategorySpec("AMENITY", "MT1", 1300),
            new CategorySpec("AMENITY", "FD6", 1300),
            new CategorySpec("AMENITY", "CE7", 1300),
            new CategorySpec("AMENITY", "CT1", 1300),
            new CategorySpec("AMENITY", "BK9", 1300),
            new CategorySpec("GREEN", "AT4", 2000));

    private final KakaoLocalPort kakaoLocalPort;
    private final NearbyFacilityRepository nearbyFacilityRepository;

    public PoiDataService(KakaoLocalPort kakaoLocalPort,
                          NearbyFacilityRepository nearbyFacilityRepository) {
        this.kakaoLocalPort = kakaoLocalPort;
        this.nearbyFacilityRepository = nearbyFacilityRepository;
    }

    public List<NearbyFacility> ensureNearby(Property property) {
        if (property.lat() == null || property.lng() == null) {
            return List.of();
        }
        final List<NearbyFacility> existing = nearbyFacilityRepository.findByPropertyId(property.id());
        if (!existing.isEmpty()) {
            return existing;
        }
        return fetchAndStore(property);
    }

    private List<NearbyFacility> fetchAndStore(Property property) {
        final List<NearbyFacility> facilities = new ArrayList<>();
        try {
            for (final CategorySpec spec : CATEGORIES) {
                final List<PoiResult> pois = kakaoLocalPort.searchCategory(
                        spec.groupCode(), property.lng().doubleValue(), property.lat().doubleValue(), spec.radiusM());
                for (final PoiResult poi : pois) {
                    facilities.add(NearbyFacility.of(
                            property.id(), spec.category(), spec.groupCode(),
                            poi.name(), poi.distanceM(), walkMinutes(poi.distanceM()), Instant.now()));
                }
            }
        } catch (RuntimeException e) {
            log.warn("POI 수집 실패 propertyId={}: {}", property.id(), e.getMessage());
            return List.of();
        }
        for (final NearbyFacility facility : facilities) {
            nearbyFacilityRepository.save(facility);
        }
        return facilities;
    }

    private static int walkMinutes(Integer distanceM) {
        if (distanceM == null) {
            return 0;
        }
        return (int) Math.round(distanceM * 1.3 / 67.0);
    }

    private record CategorySpec(String category, String groupCode, int radiusM) {
    }
}
