package banghak.home.halley.adapter.outbound.external.vworld;

import banghak.home.halley.domain.property.OfficialPrice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("V-World 공시가격 응답 파싱 (설계 I54)")
class VworldHousingPriceAdapterTest {

    /** 은마아파트 PNU 실측 응답에서 항목 두 건만 남긴 것. */
    private static final String EUNMA_PAGE = """
            {"apartHousingPrices" : {"field" : [
              {"stdrYear" : "2026", "aphusSeCode" : "1", "prvuseAr" : "84.43", "ldCode" : "1168010600",
               "ldCodeNm" : "서울특별시 강남구 대치동", "pblntfPc" : "656000000", "mnnmSlno" : "316",
               "pnu" : "1168010600103160000", "hoNm" : "1401", "dongNm" : "27",
               "floorNm" : "14", "aphusNm" : "은마", "aphusSeCodeNm" : "아파트"},
              {"stdrYear" : "2026", "aphusSeCode" : "1", "prvuseAr" : "84.43", "ldCode" : "1168010600",
               "ldCodeNm" : "서울특별시 강남구 대치동", "pblntfPc" : "662000000", "mnnmSlno" : "316",
               "pnu" : "1168010600103160000", "hoNm" : "1308", "dongNm" : "27",
               "floorNm" : "13", "aphusNm" : "은마", "aphusSeCodeNm" : "아파트"}],
             "pageNo" : "1", "resultCode" : "", "totalCount" : "2", "numOfRows" : "1000", "resultMsg" : ""}}
            """;

    private final VworldHousingPriceAdapter adapter = new VworldHousingPriceAdapter(
            mock(VworldHousingPriceFeignClient.class), new ObjectMapper(), "dummy-key");

    @Test
    @DisplayName("실측 응답에서 공시가격·기준연도·동호·전용면적을 읽는다")
    void parsesRealApartmentResponse() {
        // when
        final List<OfficialPrice> prices = adapter.parse(EUNMA_PAGE, "1168010600103160000", "apartment");

        // then
        assertThat(prices).hasSize(2);
        assertThat(prices.getFirst().price()).isEqualTo(656_000_000L);
        assertThat(prices.getFirst().year()).isEqualTo(2026);
        assertThat(prices.getFirst().dongName()).isEqualTo("27");
        assertThat(prices.getFirst().hoName()).isEqualTo("1401");
        assertThat(prices.getFirst().areaM2()).isEqualByComparingTo("84.43");
    }

    @Test
    @DisplayName("개별주택은 housePc 필드를 쓴다")
    void parsesDetachedHousePrice() {
        // given
        final String body = """
                {"indvdHousingPrices": {"field": [
                    {"pnu": "1111016700100200000", "stdrYear": "2026",
                     "ladRegstrAr": "147.8", "housePc": "268000000"}]}}
                """;

        // when
        final List<OfficialPrice> prices = adapter.parse(body, "1111016700100200000", "detached-house");

        // then
        assertThat(prices).singleElement()
                .extracting(OfficialPrice::price, OfficialPrice::year)
                .containsExactly(268_000_000L, 2026);
    }

    @Test
    @DisplayName("정상 응답의 resultCode는 빈 문자열이라 거절로 보지 않는다")
    void acceptsBlankResultCode() {
        // given — 자료가 없는 필지의 실측 응답
        final String body = """
                {"response" : {"pageNo" : "1", "resultCode" : "", "totalCount" : "0",
                 "numOfRows" : "5", "resultMsg" : ""}}
                """;

        // when
        final List<OfficialPrice> prices = adapter.parse(body, "1111014000100040000", "apartment");

        // then — 거절이 아니라 '자료 없음'이므로 빈 목록
        assertThat(prices).isEmpty();
    }

    @Test
    @DisplayName("인증 실패는 HTTP 200 + resultCode로 오므로 본문을 보고 걸러낸다")
    void rejectsInvalidKeyResponse() {
        // given — 실제 응답 그대로
        final String body = """
                {"apartHousingPrices" : {"resultCode" : "INVALID_KEY",
                 "resultMsg" : "등록되지 않은 인증키입니다."}}
                """;

        // when
        final List<OfficialPrice> prices = adapter.parse(body, "1111014000100040000", "apartment");

        // then
        assertThat(prices).isEmpty();
    }

    @Test
    @DisplayName("결과가 1건이면 배열이 아니라 객체로 오는 경우도 읽는다")
    void parsesSingleObjectResult() {
        // given
        final String body = """
                {"apartHousingPrices": {"field":
                    {"pnu": "1111014000100040000", "stdrYear": "2025",
                     "prvuseAr": "84.9", "pblntfPc": "790000000"}}}
                """;

        // when
        final List<OfficialPrice> prices = adapter.parse(body, "1111014000100040000", "apartment");

        // then
        assertThat(prices).singleElement().extracting(OfficialPrice::price).isEqualTo(790_000_000L);
    }

    @Test
    @DisplayName("stdrYear를 지정해 호출한다 — 빼면 전 연도가 오래된 순으로 나와 20년 전 값이 잡힌다")
    void alwaysRequestsASpecificYear() {
        // given
        final RecordingClient client = new RecordingClient(EUNMA_PAGE);
        final VworldHousingPriceAdapter withStub = new VworldHousingPriceAdapter(
                client, new ObjectMapper(), "dummy-key");

        // when
        withStub.fetchApartmentPrices("1168010600103160000");

        // then — 연도 탐색 1회 + 본조회 1회, 전부 올해 연도가 채워져 있다
        assertThat(client.years).isNotEmpty().doesNotContainNull();
        assertThat(client.years).allSatisfy(year -> assertThat(year).matches("\\d{4}"));
        assertThat(client.years.getFirst()).isEqualTo(String.valueOf(Year.now().getValue()));
    }

    @Test
    @DisplayName("올해 자료가 없으면 이전 연도로 거슬러 찾는다 — 공시는 매년 4월 말이라 연초에는 비어 있다")
    void fallsBackToPreviousYear() {
        // given — 올해는 0건, 작년부터 자료가 있다
        final String thisYear = String.valueOf(Year.now().getValue());
        final RecordingClient client = new RecordingClient(EUNMA_PAGE) {
            private static final String EMPTY = """
                    {"apartHousingPrices" : {"pageNo" : "1", "resultCode" : "",
                     "totalCount" : "0", "numOfRows" : "1", "resultMsg" : ""}}
                    """;

            @Override
            public String fetchApartmentPrice(String key, String pnu, String stdrYear, String format,
                                              int numOfRows, int pageNo) {
                years.add(stdrYear);
                return thisYear.equals(stdrYear) ? EMPTY : EUNMA_PAGE;
            }
        };
        final VworldHousingPriceAdapter withStub = new VworldHousingPriceAdapter(
                client, new ObjectMapper(), "dummy-key");

        // when
        final List<OfficialPrice> prices = withStub.fetchApartmentPrices("1168010600103160000");

        // then
        assertThat(client.years).containsSubsequence(thisYear, String.valueOf(Year.now().getValue() - 1));
        assertThat(prices).hasSize(2);
    }

    @Test
    @DisplayName("PNU가 19자리가 아니면 호출하지 않는다")
    void skipsInvalidPnu() {
        // given
        final RecordingClient client = new RecordingClient(EUNMA_PAGE);
        final VworldHousingPriceAdapter withStub = new VworldHousingPriceAdapter(
                client, new ObjectMapper(), "dummy-key");

        // when
        final List<OfficialPrice> prices = withStub.fetchApartmentPrices("11680106001");

        // then
        assertThat(prices).isEmpty();
        assertThat(client.years).isEmpty();
    }

    /** 어떤 연도로 호출했는지 기록하는 스텁. */
    private static class RecordingClient implements VworldHousingPriceFeignClient {

        final List<String> years = new ArrayList<>();
        private final String body;

        RecordingClient(String body) {
            this.body = body;
        }

        @Override
        public String fetchApartmentPrice(String key, String pnu, String stdrYear, String format,
                                          int numOfRows, int pageNo) {
            years.add(stdrYear);
            return body;
        }

        @Override
        public String fetchDetachedHousePrice(String key, String pnu, String stdrYear, String format,
                                              int numOfRows, int pageNo) {
            years.add(stdrYear);
            return body;
        }
    }
}
