package banghak.home.halley.adapter.outbound.external.ministry;

import banghak.home.halley.domain.property.ReferenceTrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MinistryReferenceAdapterTest {

    private final MinistryReferenceAdapter adapter = new MinistryReferenceAdapter(null, "key");

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
    @DisplayName("잘못된 XML은 예외 없이 빈 목록으로 처리한다")
    void malformedXmlReturnsEmpty() {
        // when
        final List<ReferenceTrade> trades = adapter.parse("<not-xml>");

        // then
        assertThat(trades).isEmpty();
    }

    @Test
    @DisplayName("서비스 키가 없으면 호출하지 않고 빈 목록을 반환한다")
    void blankKeyReturnsEmpty() {
        // given
        final MinistryReferenceAdapter adapter = new MinistryReferenceAdapter(null, "  ");

        // when
        final List<ReferenceTrade> trades = adapter.fetchTrades("11010", "202607");

        // then
        assertThat(trades).isEmpty();
    }
}
