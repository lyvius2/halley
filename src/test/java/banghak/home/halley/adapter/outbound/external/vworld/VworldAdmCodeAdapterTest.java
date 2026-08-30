package banghak.home.halley.adapter.outbound.external.vworld;

import banghak.home.halley.domain.geo.AdmArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("V-World 행정구역 코드 응답 파싱 (설계 I78)")
class VworldAdmCodeAdapterTest {

    /** 실측 응답. 바깥·안쪽 래퍼 이름이 `admVOList`로 같다. */
    private static final String SIDO = """
            {"admVOList" : {"pageNo" : "1", "admVOList" : [
              {"admCode" : "11", "admCodeNm" : "서울특별시", "lowestAdmCodeNm" : "서울특별시"},
              {"admCode" : "12", "admCodeNm" : "전남광주통합특별시", "lowestAdmCodeNm" : "전남광주통합특별시"},
              {"admCode" : "26", "admCodeNm" : "부산광역시", "lowestAdmCodeNm" : "부산광역시"}]}}
            """;

    private static final String SIGUNGU = """
            {"admVOList" : {"pageNo" : "1", "admVOList" : [
              {"admCode" : "41820", "admCodeNm" : "경기도 가평군", "lowestAdmCodeNm" : "가평군"},
              {"admCode" : "41281", "admCodeNm" : "경기도 고양시 덕양구", "lowestAdmCodeNm" : "고양시 덕양구"},
              {"admCode" : "41597", "admCodeNm" : "경기도 화성시 동탄구", "lowestAdmCodeNm" : "화성시 동탄구"}]}}
            """;

    private final VworldAdmCodeAdapter adapter = new VworldAdmCodeAdapter(
            mock(VworldAdmCodeFeignClient.class), new ObjectMapper(), "dummy-key");

    @Test
    @DisplayName("래퍼 이름이 안팎으로 같아도 배열을 찾아 읽는다")
    void parsesNestedSameNameWrapper() {
        // when
        final List<AdmArea> sido = adapter.parse(SIDO, "sido");

        // then
        assertThat(sido).hasSize(3);
        assertThat(sido.getFirst().code()).isEqualTo("11");
        assertThat(sido.getFirst().fullName()).isEqualTo("서울특별시");
    }

    @Test
    @DisplayName("시군구는 상위를 뺀 이름을 쓴다 — 규제지역 매칭이 그 형태를 기대한다")
    void usesLowestNameForSigungu() {
        // when
        final List<AdmArea> sigungu = adapter.parse(SIGUNGU, "sigungu:41");

        // then
        assertThat(sigungu).extracting(AdmArea::name)
                .containsExactly("가평군", "고양시 덕양구", "화성시 동탄구");
        assertThat(sigungu).extracting(AdmArea::code)
                .containsExactly("41820", "41281", "41597");
    }

    @Test
    @DisplayName("응답이 없거나 깨져도 예외 대신 빈 목록 — 기동을 막지 않는다")
    void returnsEmptyOnBadInput() {
        assertThat(adapter.parse(null, "sido")).isEmpty();
        assertThat(adapter.parse("not json", "sido")).isEmpty();
        assertThat(adapter.parse("{\"admVOList\": {\"pageNo\": \"1\"}}", "sido")).isEmpty();
    }
}
