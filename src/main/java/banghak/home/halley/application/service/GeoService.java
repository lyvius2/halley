package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.config.exception.InvalidGeoQueryException;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.property.BuildingNumber;

import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class GeoService {

    /** 같은 단지의 건물을 찾을 반경 (설계 I268) — 한 단지 안의 동은 50m 안팎, 이름이 겹치는 남의 단지는 680m 떨어져 있었다. */
    private static final int BUILDING_RADIUS_M = 300;

    private final KakaoLocalPort kakaoLocalPort;

    public GeoService(KakaoLocalPort kakaoLocalPort) {
        this.kakaoLocalPort = kakaoLocalPort;
    }

    public List<GeoSearchResult> search(String query) {
        if (query == null || query.isBlank()) {
            throw new InvalidGeoQueryException();
        }
        return kakaoLocalPort.searchAddress(query.trim());
    }

    /**
     * 같은 단지에서 동을 가려 좌표를 받는다 (설계 I268).
     * 주소검색은 동을 무시해 단지 매물이 지도에서 포개지므로, 장소검색으로 단지 좌표
     * {@value #BUILDING_RADIUS_M}m 안·이름에 동 번호가 있는 것만 받는다 — 남의 단지를 안 집는다.
     *
     * @return 못 찾으면 비어 있다 — 부르는 쪽은 단지 좌표를 그대로 쓴다
     */
    public Optional<GeoSearchResult> geocodeBuilding(String complexName, String dongHo,
                                                     BigDecimal baseLat, BigDecimal baseLng) {
        if (complexName == null || complexName.isBlank() || baseLat == null || baseLng == null) {
            return Optional.empty();
        }
        final Optional<String> building = BuildingNumber.of(dongHo);
        if (building.isEmpty()) {
            return Optional.empty();
        }
        final String dong = building.get();
        try {
            return kakaoLocalPort.searchKeyword(complexName.trim() + " " + dong, null,
                            baseLng.doubleValue(), baseLat.doubleValue(), BUILDING_RADIUS_M)
                    .stream()
                    // 반경으로 남의 단지는 걸렀지만, 이름까지 봐야 <b>같은 단지의 다른 건물</b>을 안 집는다
                    .filter(poi -> poi.name() != null && poi.name().contains(dong))
                    .filter(poi -> poi.x() != null && poi.y() != null)
                    .findFirst()
                    .map(poi -> new GeoSearchResult(poi.name(), null,
                            new BigDecimal(poi.y()), new BigDecimal(poi.x()), null, null));
        } catch (RuntimeException e) {
            // 좌표를 <b>못 얻는 것</b>이지 등록이 실패할 일이 아니다
            log.warn("Building geocoding failed - falling back to the complex coordinate. "
                    + "name={}, dongHo={}, cause={}", complexName, dongHo, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 주소로 첫 번째 지오코딩 결과를 조회한다. 주소가 없거나 변환에 실패하면 빈 값을 반환한다.
     */
    public Optional<GeoSearchResult> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        try {
            return kakaoLocalPort.searchAddress(address.trim()).stream().findFirst();
        } catch (RuntimeException e) {
            log.warn("Geocoding failed. query={}, cause={}", address.trim(), e.getMessage());
            return Optional.empty();
        }
    }
}
