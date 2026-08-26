package banghak.home.halley.adapter.outbound.external.naver;

import banghak.home.halley.config.exception.ListingCheckFailedException;
import banghak.home.halley.domain.property.ListingCheckResult;
import banghak.home.halley.domain.property.ListingVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NaverListingAliveCheckerTest {

    private final NaverListingAliveChecker checker =
            new NaverListingAliveChecker((articleNo) -> "<html>정상 매물 페이지</html>");

    @Test
    @DisplayName("정상 응답이면 ALIVE로 판정한다")
    void alive() {
        // when
        final ListingCheckResult result = checker.check("https://fin.land.naver.com/articles/123456");

        // then
        assertThat(result.verdict()).isEqualTo(ListingVerdict.ALIVE);
        assertThat(result.evidence()).isEqualTo("매물 데이터 존재");
    }

    @Test
    @DisplayName("삭제 마커가 있으면 GONE으로 판정한다")
    void gone() {
        // given
        final NaverListingAliveChecker checker = new NaverListingAliveChecker(
                (articleNo) -> "<div>매물이 없습니다.</div>");

        // when
        final ListingCheckResult result = checker.check("https://fin.land.naver.com/articles/123456");

        // then
        assertThat(result.verdict()).isEqualTo(ListingVerdict.GONE);
    }

    @Test
    @DisplayName("403/429 차단이면 BLOCKED로 판정한다")
    void blocked() {
        // given
        final NaverListingAliveChecker checker = new NaverListingAliveChecker(
                articleNo -> {
                    throw new ListingCheckFailedException(new RuntimeException("forbidden"), 403);
                });

        // when
        final ListingCheckResult result = checker.check("https://fin.land.naver.com/articles/123456");

        // then
        assertThat(result.verdict()).isEqualTo(ListingVerdict.BLOCKED);
    }

    @Test
    @DisplayName("5xx/네트워크 오류면 ERROR로 판정한다")
    void errorOnFailure() {
        // given
        final NaverListingAliveChecker checker = new NaverListingAliveChecker(
                articleNo -> {
                    throw new ListingCheckFailedException(new RuntimeException("timeout"), null);
                });

        // when
        final ListingCheckResult result = checker.check("https://fin.land.naver.com/articles/123456");

        // then
        assertThat(result.verdict()).isEqualTo(ListingVerdict.ERROR);
    }

    @Test
    @DisplayName("매물번호를 추출할 수 없으면 ERROR로 판정한다")
    void errorOnNoArticleNo() {
        // when
        final ListingCheckResult result = checker.check("https://example.com/foo");

        // then
        assertThat(result.verdict()).isEqualTo(ListingVerdict.ERROR);
        assertThat(result.evidence()).contains("articleNo 추출 실패");
    }
}
