package banghak.home.halley.adapter.outbound.external.vworld;

import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.landuse.LandUseConflict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("V-World 토지이용계획 파싱 (설계 I69)")
class VworldLandUseAdapterTest {

    /** 은마아파트 PNU 실측 응답에서 항목을 추린 것. 중복·관계 구분을 그대로 남겼다. */
    private static final String EUNMA = """
            {"landUses": {"field": [
              {"cnflcAtNm": "포함", "prposAreaDstrcCodeNm": "토지거래계약에관한허가구역",
               "prposAreaDstrcCode": "UQQ600", "pnu": "1168010600103160000",
               "manageNo": "16130001168020251058UQQ6000008032"},
              {"cnflcAtNm": "포함", "prposAreaDstrcCodeNm": "토지거래계약에관한허가구역",
               "prposAreaDstrcCode": "UQQ600", "pnu": "1168010600103160000",
               "manageNo": "16130001168020251219UQQ6000010015"},
              {"cnflcAtNm": "포함", "prposAreaDstrcCodeNm": "정비구역",
               "prposAreaDstrcCode": "UDT100", "pnu": "1168010600103160000"},
              {"cnflcAtNm": "포함", "prposAreaDstrcCodeNm": "제3종일반주거지역",
               "prposAreaDstrcCode": "UQA123", "pnu": "1168010600103160000"},
              {"cnflcAtNm": "접함", "prposAreaDstrcCodeNm": "제1종일반주거지역",
               "prposAreaDstrcCode": "UQA121", "pnu": "1168010600103160000"},
              {"cnflcAtNm": "접함", "prposAreaDstrcCodeNm": "제2종일반주거지역",
               "prposAreaDstrcCode": "UQA122", "pnu": "1168010600103160000"},
              {"cnflcAtNm": "저촉", "prposAreaDstrcCodeNm": "일반철도",
               "prposAreaDstrcCode": "UQS510", "pnu": "1168010600103160000"},
              {"cnflcAtNm": "저촉", "prposAreaDstrcCodeNm": "일반철도",
               "prposAreaDstrcCode": "UQS510", "pnu": "1168010600103160000"}],
             "pageNo": "1", "resultCode": "", "totalCount": "8", "numOfRows": "1000", "resultMsg": ""}}
            """;

    private final VworldLandUseAdapter adapter = new VworldLandUseAdapter(
            mock(VworldLandUseFeignClient.class), new ObjectMapper(), "dummy-key", null);

    @Test
    @DisplayName("같은 지역·지구가 관리번호만 달리 반복돼도 한 번만 남긴다")
    void deduplicatesRepeatedZones() {
        // when
        final List<LandUse> result = adapter.parse(EUNMA, "1168010600103160000");

        // then — 8건 중 토지거래허가구역 2건·일반철도 2건이 각각 하나로 접힌다
        assertThat(result).hasSize(6);
        assertThat(result).extracting(LandUse::zoneName)
                .containsExactlyInAnyOrder("토지거래계약에관한허가구역", "정비구역", "제3종일반주거지역",
                        "제1종일반주거지역", "제2종일반주거지역", "일반철도");
    }

    @Test
    @DisplayName("포함·저촉·접함을 구분한다 — 안 가르면 용도지역이 세 개로 보인다")
    void distinguishesConflictKind() {
        // when
        final List<LandUse> result = adapter.parse(EUNMA, "1168010600103160000");

        // then — 실제 용도지역은 제3종(포함) 하나. 1·2종은 옆 필지다
        assertThat(included(result, "제3종일반주거지역")).isTrue();
        assertThat(result).filteredOn(l -> l.zoneName().equals("제1종일반주거지역"))
                .singleElement()
                .extracting(LandUse::conflict).isEqualTo(LandUseConflict.ADJACENT);
        assertThat(result).filteredOn(l -> l.zoneName().equals("일반철도"))
                .singleElement()
                .extracting(LandUse::conflict).isEqualTo(LandUseConflict.OVERLAP);
    }

    @Test
    @DisplayName("토지거래허가구역·정비구역은 매수 조건을 가르므로 강조 대상이다")
    void marksNotableZones() {
        // when
        final List<LandUse> result = adapter.parse(EUNMA, "1168010600103160000");

        // then
        assertThat(result).filteredOn(LandUse::isNotable)
                .extracting(LandUse::zoneName)
                .containsExactlyInAnyOrder("토지거래계약에관한허가구역", "정비구역");
    }

    @Test
    @DisplayName("접함인 항목은 강조하지 않는다 — 적용되지 않는다")
    void adjacentIsNeverNotable() {
        // given — 정비구역이지만 접함
        final String body = """
                {"landUses": {"field": [
                  {"cnflcAtNm": "접함", "prposAreaDstrcCodeNm": "정비구역",
                   "prposAreaDstrcCode": "UDT100", "pnu": "1168010600103160000"}],
                 "resultCode": ""}}
                """;

        // when
        final List<LandUse> result = adapter.parse(body, "1168010600103160000");

        // then
        assertThat(result).singleElement().extracting(LandUse::isNotable).isEqualTo(false);
    }

    @Test
    @DisplayName("인증 실패는 본문 resultCode로 걸러낸다")
    void rejectsInvalidKey() {
        // given
        final String body = """
                {"landUses": {"resultCode": "INVALID_KEY", "resultMsg": "등록되지 않은 인증키입니다."}}
                """;

        // when · then
        assertThat(adapter.parse(body, "1168010600103160000")).isEmpty();
    }

    @Test
    @DisplayName("PNU가 19자리가 아니면 호출하지 않는다")
    void skipsInvalidPnu() {
        // given
        final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        final VworldLandUseFeignClient client = (key, pnu, format, rows, page, domain) -> {
            calls.incrementAndGet();
            return EUNMA;
        };
        final VworldLandUseAdapter withStub =
                new VworldLandUseAdapter(client, new ObjectMapper(), "dummy-key", null);

        // when
        final List<LandUse> result = withStub.fetch("11680106");

        // then
        assertThat(result).isEmpty();
        assertThat(calls.get()).isZero();
    }

    private boolean included(List<LandUse> items, String name) {
        return items.stream().anyMatch(l -> l.zoneName().equals(name)
                && l.conflict() == LandUseConflict.INCLUDED);
    }
}
