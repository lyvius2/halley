package banghak.home.halley.adapter.outbound.external.kakao;

import banghak.home.halley.domain.geo.GeoSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoLocalAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("카카오 주소검색 응답 JSON을 GeoSearchResult 목록으로 매핑한다")
    void mapDocuments() {
        // given
        final KakaoLocalAdapter adapter = new KakaoLocalAdapter("key", objectMapper);
        final JsonNode root = objectMapper.readTree("""
                {"documents":[
                    {"address_name":"서울 마포구 서교동","road_address_name":"서울 마포구 양화로","x":"126.91","y":"37.55"}
                ]}
                """);

        // when
        final List<GeoSearchResult> results = adapter.mapDocuments(root);

        // then
        assertThat(results).hasSize(1);
        final GeoSearchResult result = results.getFirst();
        assertThat(result.addressName()).isEqualTo("서울 마포구 서교동");
        assertThat(result.roadAddressName()).isEqualTo("서울 마포구 양화로");
        assertThat(result.lat()).isEqualByComparingTo("37.55");
        assertThat(result.lng()).isEqualByComparingTo("126.91");
    }

    @Test
    @DisplayName("documents가 비어 있으면 빈 목록을 반환한다")
    void emptyDocuments() throws Exception {
        // given
        final KakaoLocalAdapter adapter = new KakaoLocalAdapter("key", objectMapper);
        final JsonNode root = objectMapper.readTree("{\"documents\":[]}");

        // when
        final List<GeoSearchResult> results = adapter.mapDocuments(root);

        // then
        assertThat(results).isEmpty();
    }
}
