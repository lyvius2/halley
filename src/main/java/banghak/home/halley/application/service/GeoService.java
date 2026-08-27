package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.config.exception.InvalidGeoQueryException;
import banghak.home.halley.domain.geo.GeoSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class GeoService {

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
     * 주소로 첫 번째 지오코딩 결과를 조회한다. 주소가 없거나 변환에 실패하면 빈 값을 반환한다.
     */
    public Optional<GeoSearchResult> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        try {
            return kakaoLocalPort.searchAddress(address.trim()).stream().findFirst();
        } catch (RuntimeException e) {
            log.warn("지오코딩 실패 query={}: {}", address.trim(), e.getMessage());
            return Optional.empty();
        }
    }
}
