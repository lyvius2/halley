package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.external.law.RegulationNoticePdfParser;
import banghak.home.halley.domain.geo.LegalDongCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("고시 축약 지역명 → 시군구 코드 매칭 (설계 I78)")
class SigunguNameMatcherTest {

    private final SigunguNameMatcher matcher = new SigunguNameMatcher();

    @Test
    @DisplayName("시와 구를 붙여 줄인 이름을 정식명칭에 맞춘다")
    void matchesAbbreviatedNames() {
        // given
        final List<LegalDongCode> dict = dictionary(
                "1168010100", "서울특별시", "강남구",
                "4111100000", "경기도", "수원시 장안구",
                "4159700000", "경기도", "화성시 동탄구",
                "4139000000", "경기도", "과천시");

        // when
        final Map<String, SigunguNameMatcher.Matched> matched = matcher.match(
                List.of("서울 강남구", "경기 수원장안", "경기 화성동탄", "경기 과천"), dict);

        // then — 접미사를 떼면 양쪽이 같아진다
        assertThat(matched).hasSize(4);
        assertThat(matched.get("경기 화성동탄").name()).isEqualTo("경기도 화성시 동탄구");
        assertThat(matched.get("경기 화성동탄").code()).isEqualTo("41597");
        assertThat(matched.get("경기 과천").name()).isEqualTo("경기도 과천시");
        assertThat(matched.get("서울 강남구").code()).isEqualTo("11680");
    }

    @Test
    @DisplayName("같은 이름이 여러 시도에 있어도 시도 안에서만 찾아 섞이지 않는다")
    void scopesBySido() {
        // given — 정규화하면 서울 중구·부산 중구가 모두 '중'이 된다
        final List<LegalDongCode> dict = dictionary(
                "1114000000", "서울특별시", "중구",
                "2611000000", "부산광역시", "중구");

        // when
        final Map<String, SigunguNameMatcher.Matched> matched =
                matcher.match(List.of("서울 중구"), dict);

        // then
        assertThat(matched.get("서울 중구").code()).isEqualTo("11140");
    }

    @Test
    @DisplayName("하나라도 못 찾으면 통째로 버린다 — 일부만 넣으면 나머지가 비규제로 잡힌다")
    void discardsAllWhenAnyUnmatched() {
        // given — 사전에 구리시가 없다
        final List<LegalDongCode> dict = dictionary("1168010100", "서울특별시", "강남구");

        // when
        final Map<String, SigunguNameMatcher.Matched> matched =
                matcher.match(List.of("서울 강남구", "경기 구리"), dict);

        // then
        assertThat(matched).isEmpty();
    }

    @Test
    @DisplayName("사전이 비면 빈 결과 — 없는 것을 있는 척하지 않는다")
    void requiresDictionary() {
        assertThat(matcher.match(List.of("서울 강남구"), List.of())).isEmpty();
    }

    @Test
    @DisplayName("실물 고시 40곳이 전부 매칭된다")
    void matchesEveryAreaInRealNotice() throws IOException {
        // given — 국토교통부공고 제2026-883호에서 뽑은 이름들
        final List<String> areas = new RegulationNoticePdfParser()
                .parseAreaNames(fixture("speculation-notice-2026-883.pdf"));
        assertThat(areas).hasSize(40);

        // when
        final Map<String, SigunguNameMatcher.Matched> matched = matcher.match(areas, realDictionary());

        // then — 하나라도 빠지면 match()가 통째로 버리므로 40이 아니면 실패다
        assertThat(matched).hasSize(40);
        assertThat(matched.get("경기 화성동탄").name()).isEqualTo("경기도 화성시 동탄구");
        assertThat(matched.get("경기 용인기흥").name()).isEqualTo("경기도 용인시 기흥구");
        assertThat(matched.get("서울 도봉구").name()).isEqualTo("서울특별시 도봉구");
    }

    /** 고시에 나오는 시군구의 실제 법정동코드. */
    private List<LegalDongCode> realDictionary() {
        final List<String> seoul = List.of(
                "11110 종로구", "11140 중구", "11170 용산구", "11200 성동구", "11215 광진구",
                "11230 동대문구", "11260 중랑구", "11290 성북구", "11305 강북구", "11320 도봉구",
                "11350 노원구", "11380 은평구", "11410 서대문구", "11440 마포구", "11470 양천구",
                "11500 강서구", "11530 구로구", "11545 금천구", "11560 영등포구", "11590 동작구",
                "11620 관악구", "11650 서초구", "11680 강남구", "11710 송파구", "11740 강동구");
        final List<String> gyeonggi = List.of(
                "41111 수원시 장안구", "41113 수원시 권선구", "41115 수원시 팔달구",
                "41117 수원시 영통구", "41131 성남시 수정구", "41133 성남시 중원구",
                "41135 성남시 분당구", "41171 안양시 만안구", "41173 안양시 동안구",
                "41210 광명시", "41290 과천시", "41450 하남시", "41461 용인시 처인구",
                "41463 용인시 기흥구", "41465 용인시 수지구", "41430 의왕시", "41310 구리시",
                "41590 화성시", "41597 화성시 동탄구");
        final List<LegalDongCode> dict = new ArrayList<>();
        seoul.forEach(row -> dict.add(entry(row, "서울특별시")));
        gyeonggi.forEach(row -> dict.add(entry(row, "경기도")));
        return dict;
    }

    private LegalDongCode entry(String row, String sido) {
        final int space = row.indexOf(' ');
        // 법정동코드는 10자리이고 시군구는 앞 5자리다
        return new LegalDongCode(row.substring(0, space) + "00000", sido,
                row.substring(space + 1), null, null, true, Instant.now());
    }

    private List<LegalDongCode> dictionary(String... triples) {
        final List<LegalDongCode> dict = new ArrayList<>();
        for (int i = 0; i < triples.length; i += 3) {
            dict.add(new LegalDongCode(triples[i], triples[i + 1], triples[i + 2],
                    null, null, true, Instant.now()));
        }
        return dict;
    }

    private byte[] fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/law/" + name)) {
            return in.readAllBytes();
        }
    }
}
