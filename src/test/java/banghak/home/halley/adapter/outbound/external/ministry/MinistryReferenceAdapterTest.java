package banghak.home.halley.adapter.outbound.external.ministry;

import banghak.home.halley.config.RateGate;
import banghak.home.halley.domain.property.ReferenceTrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MinistryReferenceAdapterTest {

    private final MinistryReferenceAdapter adapter = new MinistryReferenceAdapter(null, new RateGate("test", 0), "key");

    @Test
    @DisplayName("국토부 XML 응답을 ReferenceTrade로 매핑한다")
    void parseXml() {
        // given
        final String xml = """
                <response><body><items>
                <item>
                <거래금액>92,500</거래금액>
                <건축년도>1995</건축년도>
                <아파트>독립문삼호</아파트>
                <전용면적>84.93</전용면적>
                <층>7</층>
                <년>2026</년>
                <월>7</월>
                <일>12</일>
                </item>
                </items></body></response>
                """;

        // when
        final List<ReferenceTrade> trades = adapter.parse(xml);

        // then
        assertThat(trades).hasSize(1);
        final ReferenceTrade trade = trades.getFirst();
        assertThat(trade.apartmentName()).isEqualTo("독립문삼호");
        assertThat(trade.dealAmount()).isEqualTo(925_000_000L);
        assertThat(trade.areaM2()).isEqualByComparingTo("84.93");
        assertThat(trade.floorNo()).isEqualTo(7);
        assertThat(trade.contractDate()).isEqualTo("2026-07-12");
    }

    @Test
    @DisplayName("현행 apis.data.go.kr의 영문 태그 응답도 ReferenceTrade로 매핑한다")
    void parseEnglishTagXml() {
        // given — apis.data.go.kr/1613000 실제 응답 형식
        final String xml = """
                <response><body><items>
                <item>
                <aptNm>에비뉴청계Ⅱ</aptNm>
                <dealAmount> 92,500</dealAmount>
                <excluUseAr>84.93</excluUseAr>
                <floor>7</floor>
                <dealYear>2026</dealYear>
                <dealMonth>7</dealMonth>
                <dealDay>12</dealDay>
                </item>
                </items></body></response>
                """;

        // when
        final List<ReferenceTrade> trades = adapter.parse(xml);

        // then
        assertThat(trades).hasSize(1);
        final ReferenceTrade trade = trades.getFirst();
        assertThat(trade.apartmentName()).isEqualTo("에비뉴청계Ⅱ");
        assertThat(trade.dealAmount()).isEqualTo(925_000_000L);
        assertThat(trade.areaM2()).isEqualByComparingTo("84.93");
        assertThat(trade.floorNo()).isEqualTo(7);
        assertThat(trade.contractDate()).isEqualTo("2026-07-12");
    }

    @Test
    @DisplayName("Encoding 형태로 발급된 서비스 키는 퍼센트 이스케이프를 되돌려 이중 인코딩(403)을 막는다")
    void decodesPercentEncodedServiceKey() {
        // given
        final String encoded = "abc%2Fdef%2Bghi%3D%3D";

        // when
        final String decoded = MinistryReferenceAdapter.decodeIfEncoded(encoded);

        // then
        assertThat(decoded).isEqualTo("abc/def+ghi==");
    }

    @Test
    @DisplayName("Decoding 형태로 발급된 키는 그대로 두며 Base64의 +를 공백으로 바꾸지 않는다")
    void keepsPlainServiceKey() {
        // when
        final String plain = MinistryReferenceAdapter.decodeIfEncoded("abc/def+ghi==");

        // then
        assertThat(plain).isEqualTo("abc/def+ghi==");
    }

    @Test
    @DisplayName("잘못된 XML은 예외 없이 빈 목록으로 처리한다")
    void malformedXmlReturnsEmpty() {
        // when
        final List<ReferenceTrade> trades = adapter.parse("<not-xml>");

        // then
        assertThat(trades).isEmpty();
    }

    @Test
    @DisplayName("서비스 키가 없으면 호출하지 않고 null — 빈 목록으로 주면 '거래 0건'으로 굳는다 (설계 I140)")
    void blankKeyReturnsNull() {
        // given
        final MinistryReferenceAdapter adapter =
                new MinistryReferenceAdapter(null, new RateGate("test", 0), "  ");

        // when
        final List<ReferenceTrade> trades = adapter.fetchTrades("11010", "202607");

        // then — 빈 목록이 아니라 null 이어야 한다. 캐시가 '모르는 것'과 '없는 것'을 구분한다
        assertThat(trades).isNull();
    }
}
