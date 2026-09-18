package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.cache.PoiCache;
import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.domain.geo.GreenCategory;
import banghak.home.halley.domain.geo.PoiResult;
import banghak.home.halley.domain.property.NearbyFacility;
import banghak.home.halley.domain.property.Property;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class PoiDataService {

    private static final int POI_SCHEMA_VERSION = 2;

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

    private static final List<KeywordSpec> GREEN_KEYWORDS = List.of(
            new KeywordSpec("공원", null, 2000),
            new KeywordSpec("하천", null, 2000),
            new KeywordSpec("산", "AT4", 2000));

    private final KakaoLocalPort kakaoLocalPort;
    private final PoiCache poiCache;

    public PoiDataService(KakaoLocalPort kakaoLocalPort, PoiCache poiCache) {
        this.kakaoLocalPort = kakaoLocalPort;
        this.poiCache = poiCache;
    }

    public List<NearbyFacility> ensureNearby(Property property) {
        if (property.lat() == null || property.lng() == null) {
            return List.of();
        }
        final List<NearbyFacility> cached = poiCache.get(property.id(), POI_SCHEMA_VERSION);
        if (!cached.isEmpty()) {
            return cached;
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
                    toFacility(property, spec.category(), spec.groupCode(), poi).ifPresent(facilities::add);
                }
            }
            for (final KeywordSpec spec : GREEN_KEYWORDS) {
                final List<PoiResult> pois = kakaoLocalPort.searchKeyword(
                        spec.query(), spec.categoryGroupCode(),
                        property.lng().doubleValue(), property.lat().doubleValue(), spec.radiusM());
                for (final PoiResult poi : pois) {
                    toFacility(property, "GREEN", null, poi).ifPresent(facilities::add);
                }
            }
        } catch (RuntimeException e) {
            log.warn("POI collection failed. propertyId={}, cause={}", property.id(), e.getMessage());
            return List.of();
        }
        poiCache.put(property.id(), POI_SCHEMA_VERSION, facilities);
        return facilities;
    }

    private Optional<NearbyFacility> toFacility(Property property, String category, String groupCode, PoiResult poi) {
        String subCategory = groupCode;
        if ("GREEN".equals(category)) {
            final Optional<GreenCategory> green = GreenCategory.classify(poi.categoryName());
            if (green.isEmpty()) {
                return Optional.empty();
            }
            subCategory = green.get().name();
        }
        return Optional.of(NearbyFacility.of(
                property.id(), category, subCategory,
                poi.name(), poi.distanceM(), walkMinutes(poi.distanceM()), Instant.now()));
    }

    private static int walkMinutes(Integer distanceM) {
        if (distanceM == null) {
            return 0;
        }
        return (int) Math.round(distanceM * 1.3 / 67.0);
    }

    private record CategorySpec(String category, String groupCode, int radiusM) {
    }

    private record KeywordSpec(String query, String categoryGroupCode, int radiusM) {
    }
}
