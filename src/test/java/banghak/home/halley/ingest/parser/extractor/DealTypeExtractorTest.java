package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.TextDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 거래유형은 <b>제목</b>이 말한다 (설계 I283).
 *
 * <p>본문 아래쪽에는 "매매 / 전세 / 월세" 를 고르는 토글 글자가 그대로 붙어 오므로,
 * 훑어 읽으면 매매 매물이 <b>전세로 읽힙니다.</b> 매매와 전세는 순위표가 아예 달라
 * (AGENTS.md) 조용히 틀리면 매물이 딴 표에 실립니다.
 */
@DisplayName("거래유형 가려내기 (설계 I283)")
class DealTypeExtractorTest {

    private final DealTypeExtractor extractor = new DealTypeExtractor();

    @Test
    @DisplayName("아래쪽 토글보다 제목을 믿는다")
    void prefersTheTitleOverTheToggles() {
        // given — 제목은 매매인데 아래에 전세 토글이 먼저 나온다
        final TextDocument doc = new TextDocument("""
                휘경롯데 102동매매 8억 8,0003,481만원/3.3㎡평당가 도움말

                동일면적 매매

                    매매현재 위치
                    전세
                    월세
                """);

        // when / then
        assertThat(extractor.extract(doc).value()).isEqualTo("매매");
    }

    @Test
    @DisplayName("제목에 유형이 없으면 값 라벨이 말해 준다")
    void fallsBackToThePriceLabel() {
        // given
        final TextDocument doc = new TextDocument("""
                어느단지 101동

                기본 정보

                    전세가
                    4억원
                """);

        // when / then
        assertThat(extractor.extract(doc).value()).isEqualTo("전세");
    }

    @Test
    @DisplayName("전세 제목은 전세로 읽는다")
    void readsJeonseTitles() {
        // given
        final TextDocument doc = new TextDocument("""
                어느단지 101동전세 4억원

                    매매
                """);

        // when / then
        assertThat(extractor.extract(doc).value()).isEqualTo("전세");
    }
}
