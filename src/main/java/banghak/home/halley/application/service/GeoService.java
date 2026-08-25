package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.config.exception.InvalidGeoQueryException;
import banghak.home.halley.domain.geo.GeoSearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
