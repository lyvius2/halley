package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.config.exception.KakaoApiKeyMissingException;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.geo.PoiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KakaoLocalAdapterTest {

    private static final String ADDRESS_JSON = """
            {"documents":[
                {"address_name":"서울 마포구 서교동","road_address_name":"서울 마포구 양화로","x":"126.91","y":"37.55"}
            ]}
            """;
    private static final String CATEGORY_JSON = """
            {"documents":[
                {"place_name":"마포역","category_group_code":"SW8","distance":"120","x":"126.90","y":"37.54"},
                {"place_name":"홍대입구역","category_group_code":"SW8","distance":"400","x":"126.92","y":"37.55"}
            ]}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("주소 검색은 Feign 응답을 GeoSearchResult 목록으로 변환해 반환한다")
    void searchAddressReturnsParsedResults() {
        // given
        final KakaoLocalFeignClient client = stubClient(ADDRESS_JSON, null);
        final KakaoLocalAdapter adapter = new KakaoLocalAdapter(client, "key", objectMapper);

        // when
        final List<GeoSearchResult> results = adapter.searchAddress("서울 마포구");

        // then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().addressName()).isEqualTo("서울 마포구 서교동");
        assertThat(results.getFirst().lat()).isEqualByComparingTo("37.55");
        assertThat(results.getFirst().lng()).isEqualByComparingTo("126.91");
    }

    @Test
    @DisplayName("카테고리 검색은 Feign 응답을 PoiResult 목록으로 변환해 반환한다")
    void searchCategoryReturnsParsedPois() {
        // given
        final KakaoLocalFeignClient client = stubClient(null, CATEGORY_JSON);
        final KakaoLocalAdapter adapter = new KakaoLocalAdapter(client, "key", objectMapper);

        // when
        final List<PoiResult> results = adapter.searchCategory("SW8", 126.9, 37.5, 1000);

        // then
        assertThat(results).hasSize(2);
        assertThat(results.getFirst().name()).isEqualTo("마포역");
        assertThat(results.getFirst().categoryGroupCode()).isEqualTo("SW8");
        assertThat(results.getFirst().distanceM()).isEqualTo(120);
    }

    @Test
    @DisplayName("Feign 폴백(실패) 시 빈 목록으로 우아하게 처리한다")
    void fallbackReturnsEmpty() {
        // given
        final KakaoLocalFeignClient client = stubClient(null, null);
        final KakaoLocalAdapter adapter = new KakaoLocalAdapter(client, "key", objectMapper);

        // when / then
        assertThat(adapter.searchAddress("서울")).isEmpty();
        assertThat(adapter.searchCategory("SW8", 0, 0, 1000)).isEmpty();
    }

    @Test
    @DisplayName("카카오 키가 없으면 KakaoApiKeyMissingException이 발생한다")
    void missingKeyThrows() {
        // given
        final KakaoLocalAdapter adapter = new KakaoLocalAdapter(stubClient(null, null), "  ", objectMapper);

        // when
        final KakaoApiKeyMissingException ex = assertThrows(
                KakaoApiKeyMissingException.class,
                () -> adapter.searchAddress("서울"));

        // then
        assertThat(ex.getCode()).isEqualTo("KAKAO_KEY_MISSING");
    }

    private static KakaoLocalFeignClient stubClient(String addressJson, String categoryJson) {
        return new KakaoLocalFeignClient() {
            @Override
            public String searchAddress(String query) {
                return addressJson;
            }

            @Override
            public String searchCategory(String categoryGroupCode, String x, String y, int radius) {
                return categoryJson;
            }
        };
    }
}
