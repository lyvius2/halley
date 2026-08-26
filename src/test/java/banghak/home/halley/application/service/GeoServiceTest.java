package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.config.exception.InvalidGeoQueryException;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.geo.PoiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeoServiceTest {

    @Test
    @DisplayName("검색어가 공백이면 InvalidGeoQueryException이 발생한다")
    void blankQueryThrows() {
        // given
        final GeoService service = new GeoService(stubPort(null));

        // when
        final InvalidGeoQueryException ex = assertThrows(
                InvalidGeoQueryException.class,
                () -> service.search("   "));

        // then
        assertThat(ex.getCode()).isEqualTo("INVALID_GEO_QUERY");
    }

    @Test
    @DisplayName("검색어를 전달하면 포트가 반환한 좌표 결과를 그대로 돌려준다")
    void searchReturnsPortResults() {
        // given
        final GeoSearchResult expected = new GeoSearchResult(
                "서울 마포구 서교동", "서울 마포구 양화로", new BigDecimal("37.55"), new BigDecimal("126.91"));
        final GeoService service = new GeoService(stubPort(expected));

        // when
        final List<GeoSearchResult> results = service.search("  서울시 마포구  ");

        // then
        assertThat(results).containsExactly(expected);
    }

    private static KakaoLocalPort stubPort(GeoSearchResult result) {
        return new KakaoLocalPort() {
            @Override
            public List<GeoSearchResult> searchAddress(String query) {
                return result == null ? List.of() : List.of(result);
            }

            @Override
            public List<PoiResult> searchCategory(String categoryGroupCode, double x, double y, int radius) {
                return List.of();
            }
        };
    }
}
