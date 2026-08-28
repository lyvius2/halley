package banghak.home.halley.domain.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GreenCategoryTest {

    @Test
    @DisplayName("공원 계열 category_name은 PARK으로 분류한다")
    void classifiesPark() {
        // then — 카카오 키워드 '공원' 실측 응답
        assertThat(GreenCategory.classify("여행 > 공원")).contains(GreenCategory.PARK);
        assertThat(GreenCategory.classify("여행 > 공원 > 도시근린공원")).contains(GreenCategory.PARK);
    }

    @Test
    @DisplayName("산·숲·자연휴양림은 MOUNTAIN으로 분류한다")
    void classifiesMountain() {
        // then — 카카오 AT4 실측 응답
        assertThat(GreenCategory.classify("여행 > 관광,명소 > 산")).contains(GreenCategory.MOUNTAIN);
        assertThat(GreenCategory.classify("여행 > 관광,명소 > 자연휴양림")).contains(GreenCategory.MOUNTAIN);
        assertThat(GreenCategory.classify("여행 > 관광,명소 > 숲")).contains(GreenCategory.MOUNTAIN);
    }

    @Test
    @DisplayName("하천은 RIVER로 분류한다")
    void classifiesRiver() {
        // then
        assertThat(GreenCategory.classify("여행 > 관광,명소 > 하천")).contains(GreenCategory.RIVER);
    }

    @Test
    @DisplayName("공원시설물·화장실·먹자골목 등 녹지가 아닌 결과는 분류하지 않는다")
    void rejectsNonGreenPlaces() {
        // then — 키워드 검색에 섞여 들어오는 실측 오탐들
        assertThat(GreenCategory.classify("여행 > 공원시설물")).isEmpty();
        assertThat(GreenCategory.classify("가정,생활 > 화장실")).isEmpty();
        assertThat(GreenCategory.classify("교통,수송 > 입출구")).isEmpty();
        assertThat(GreenCategory.classify("여행 > 관광,명소 > 테마거리 > 먹자골목")).isEmpty();
    }

    @Test
    @DisplayName("장소명이 아니라 category_name으로 판정하므로 '떡산 롯데백화점'류 오탐이 걸러진다")
    void rejectsNameLookalikes() {
        // given — place_name은 '떡산 롯데백화점노원점', '산과맥주'지만 카테고리는 음식점
        // then
        assertThat(GreenCategory.classify("음식점 > 분식")).isEmpty();
        assertThat(GreenCategory.classify("음식점 > 술집 > 호프,요리주점")).isEmpty();
        assertThat(GreenCategory.classify("부동산 > 부동산서비스 > 부동산중개업")).isEmpty();
    }

    @Test
    @DisplayName("category_name이 비어 있으면 분류하지 않는다")
    void rejectsBlank() {
        // then
        assertThat(GreenCategory.classify(null)).isEqualTo(Optional.empty());
        assertThat(GreenCategory.classify("  ")).isEmpty();
    }
}
