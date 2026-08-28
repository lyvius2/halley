package banghak.home.halley.domain.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PNU(필지고유번호) 조립 — 공시가격 조회 키 (설계 I54)")
class GeoSearchResultPnuTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("법정동코드·본번·부번을 19자리로 0채움해 붙인다")
    void buildsPnuFromKakaoAddress() {
        // given — 서울시 종로구 명륜2가 4 (부번 없음)
        final JsonNode address = address("1111014000", "4", "", "N");

        // when
        final String pnu = GeoSearchResult.pnu(address);

        // then
        assertThat(pnu).isEqualTo("1111014000100040000");
        assertThat(pnu).hasSize(19);
    }

    @Test
    @DisplayName("부번이 있으면 뒤 4자리에 0채움해 넣는다")
    void includesSubAddressNo() {
        // when
        final String pnu = GeoSearchResult.pnu(address("1168010100", "12", "34", "N"));

        // then
        assertThat(pnu).isEqualTo("1168010100100120034");
    }

    @Test
    @DisplayName("산 번지는 필지구분이 2다")
    void marksMountainParcel() {
        // when
        final String pnu = GeoSearchResult.pnu(address("1168010100", "12", "", "Y"));

        // then — 11번째 자리가 2
        assertThat(pnu).isEqualTo("1168010100200120000");
    }

    @Test
    @DisplayName("법정동코드나 본번이 없으면 만들지 않는다")
    void returnsNullWhenIncomplete() {
        assertThat(GeoSearchResult.pnu(address(null, "4", "", "N"))).isNull();
        assertThat(GeoSearchResult.pnu(address("1111014000", null, "", "N"))).isNull();
        // 10자리가 아닌 코드도 거른다
        assertThat(GeoSearchResult.pnu(address("11110", "4", "", "N"))).isNull();
    }

    @Test
    @DisplayName("카카오 주소검색 응답을 매핑하면 법정동코드와 PNU가 함께 채워진다")
    void mapsDocumentsWithPnu() {
        // given
        final JsonNode root = objectMapper.readTree("""
                {"documents": [{
                  "address_name": "서울 종로구 명륜2가 4",
                  "x": "126.99", "y": "37.58",
                  "road_address": {"address_name": "서울 종로구 성균관로 12"},
                  "address": {"b_code": "1111014000", "main_address_no": "4",
                              "sub_address_no": "", "mountain_yn": "N"}
                }]}
                """);

        // when
        final List<GeoSearchResult> results = GeoSearchResult.mapDocuments(root);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().legalDongCode()).isEqualTo("1111014000");
        assertThat(results.getFirst().pnu()).isEqualTo("1111014000100040000");
        assertThat(results.getFirst().lat()).isEqualByComparingTo(new BigDecimal("37.58"));
    }

    private JsonNode address(String bCode, String main, String sub, String mountainYn) {
        final var node = objectMapper.createObjectNode();
        if (bCode != null) {
            node.put("b_code", bCode);
        }
        if (main != null) {
            node.put("main_address_no", main);
        }
        node.put("sub_address_no", sub);
        node.put("mountain_yn", mountainYn);
        return node;
    }
}
