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

    /**
     * 수집 규칙 버전 — 캐시 키에 포함된다(설계 I44). 아래 중 하나라도 바뀌면 **반드시 올린다**.
     * 올리고 배포하면 옛 캐시가 즉시 무시되고 전량 재수집되므로 수동 삭제가 필요 없다.
     * <ul>
     *   <li>CATEGORIES · GREEN_KEYWORDS (수집 대상·반경)</li>
     *   <li>sub_category 분류 규칙 (GreenCategory)</li>
     *   <li>도보시간 환산식</li>
     * </ul>
     * v2 — GREEN을 category_name으로 분류하고 공원·하천·산을 키워드로 수집하도록 변경 (설계 I42)
     */
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

    /**
     * 공원·하천·산은 카카오에 전용 카테고리 그룹코드가 없어 키워드로 찾는다(설계 3.1 · I5).
     * 산은 키워드만 쓰면 "떡산 롯데백화점"·"산과맥주"가 걸리므로 <b>`AT4`(관광명소) 필터를 함께</b> 건다.
     * `AT4` 카테고리 검색(위 CATEGORIES)은 페이지당 15건이라 산이 잘려나갈 수 있어, 두 경로를 모두 쓴다.
     */
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
            log.warn("POI 수집 실패 propertyId={}: {}", property.id(), e.getMessage());
            return List.of();
        }
        poiCache.put(property.id(), POI_SCHEMA_VERSION, facilities);
        return facilities;
    }

    /**
     * `GREEN`은 `category_name`으로 공원·산·하천을 분류해 `sub_category`에 남기고, 셋 중 어디에도 해당하지 않는
     * 결과(테마거리·화장실·음수대 등)는 버린다. 다른 카테고리는 기존대로 그룹코드를 `sub_category`로 쓴다.
     */
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
