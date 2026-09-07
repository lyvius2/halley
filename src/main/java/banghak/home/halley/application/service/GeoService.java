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

 /** 같은 단지의 건물을 찾을 반경. 한 단지 안의 동은 50m 안팎, 이름이 겹치는 남의 단지는 680m 떨어져 있었다. */
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

 /** 같은 단지에서 동을 가려 좌표를 받는다. */
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
                    .filter(poi -> poi.name() != null && poi.name().contains(dong))
                    .filter(poi -> poi.x() != null && poi.y() != null)
                    .findFirst()
                    .map(poi -> new GeoSearchResult(poi.name(), null,
                            new BigDecimal(poi.y()), new BigDecimal(poi.x()), null, null));
        } catch (RuntimeException e) {
            log.warn("Building geocoding failed - falling back to the complex coordinate. "
                    + "name={}, dongHo={}, cause={}", complexName, dongHo, e.getMessage());
            return Optional.empty();
        }
    }

 /** 주소로 첫 번째 지오코딩 결과를 조회한다. 주소가 없거나 변환에 실패하면 빈 값을 반환한다. */
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
