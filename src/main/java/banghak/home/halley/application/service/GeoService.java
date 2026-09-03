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

    /**
     * 같은 단지의 건물을 찾을 반경 (설계 I268).
     *
     * <p>좁으면 큰 단지의 먼 동을 놓치고, 넓으면 <b>남의 단지</b>를 집습니다.
     * 실제로 재 보니 한 단지 안의 동은 <b>50m 안팎</b>이고, 이름이 겹치는 남의 단지는
     * <b>680m</b> 떨어져 있었습니다.
     */
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
     * 같은 단지에서 <b>동을 가려 좌표를 받는다</b> (설계 I268).
     *
     * <p>주소검색(`/search/address`)은 <b>동을 무시합니다.</b> 실제로 확인했습니다 —
     * `정릉동 1037` 과 `정릉동 1037 102동` 이 <b>같은 좌표</b>를 돌려줍니다.
     * 그래서 같은 단지 매물이 지도에서 정확히 포개졌습니다.
     *
     * <p>장소검색(`/search/keyword`)은 다릅니다. 102동과 104동이 <b>45m 떨어진</b>
     * 서로 다른 좌표로 옵니다.
     *
     * <h4>반드시 단지 좌표 둘레에서만 찾는다</h4>
     *
     * <p>`석관래미안 101동` 으로 물으면 <b>래미안석관</b>과 <b>래미안아트리치</b>가
     * 함께 나옵니다 — 680m 떨어진 <b>남의 단지</b>입니다. 첫 결과를 그냥 쓰면
     * 엉뚱한 곳에 핀이 찍히고, <b>그 잘못은 화면에 드러나지 않습니다.</b>
     *
     * <p>그래서 단지 좌표를 중심으로 {@value #BUILDING_RADIUS_M}m 안에서만 찾고,
     * 이름에 그 동 번호가 실제로 들어 있는 것만 받습니다.
     *
     * @return 못 찾으면 <b>비어 있다</b> — 부르는 쪽은 단지 좌표를 그대로 쓴다
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
