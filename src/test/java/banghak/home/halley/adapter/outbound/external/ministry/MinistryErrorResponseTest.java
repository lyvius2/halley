package banghak.home.halley.adapter.outbound.external.ministry;

import banghak.home.halley.config.RateGate;
import banghak.home.halley.domain.property.ReferenceTrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 국토부는 <b>오류도 200으로 준다</b> (설계 I251).
 *
 * <p>그 본문에는 {@code <item>} 이 없어 파서가 <b>빈 목록</b>을 돌려주고, 수집기는
 * 그것을 <b>"그 달은 거래가 없었다"</b>로 저장합니다. 과거 달은 다시 받지 않으므로
 * ([I128]) <b>영영 구멍</b>이 됩니다.
 *
 * <p>[I140]에서 "실패를 캐시에 굳히지 않는다"고 고쳤는데 <b>연결 실패만</b>
 * 막았습니다 — 429 는 연결이 되고 200 이 오므로 그 그물을 그냥 통과했습니다.
 *
 * <h4>빈 목록과 null 은 다른 말이다</h4>
 *
 * <pre>
 * null      못 받았다        → 저장하지 않는다 → 다음에 다시 받는다
 * List.of() 받았는데 0건이다  → 저장한다      → 다시 안 받는다
 * </pre>
 */
@DisplayName("국토부 오류 응답 (설계 I251)")
class MinistryErrorResponseTest {

    private static final String RATE_LIMITED = """
            <response><header>
              <resultCode>22</resultCode>
              <resultMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</resultMsg>
            </header><body/></response>
            """;
    private static final String OK_EMPTY = """
            <response><header>
              <resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg>
            </header><body><items/><totalCount>0</totalCount></body></response>
            """;
    private static final String OK_ONE = """
            <response><header><resultCode>00</resultCode></header><body><items>
              <item><aptNm>송산</aptNm><excluUseAr>58.59</excluUseAr>
                    <dealAmount> 70,000</dealAmount><dealYear>2026</dealYear>
                    <dealMonth>7</dealMonth><dealDay>14</dealDay><floor>18</floor>
                    <umdNm>정릉동</umdNm><jibun>1037</jibun></item>
            </items><totalCount>1</totalCount></body></response>
            """;

    /**
     * <b>이 테스트가 이번 버그의 전부입니다.</b>
     *
     * <p>429 를 맞은 달이 "거래 0건"으로 저장되면, 그 단지는 <b>몇 년이 지나도</b>
     * 실거래 지표가 안 나옵니다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"22", "20", "30", "99", "SERVICE_KEY_IS_NOT_REGISTERED_ERROR"})
    @DisplayName("오류 코드가 오면 못 받은 것으로 본다 — 0건으로 굳히지 않는다")
    void treatsErrorCodesAsNotFetched(String code) {
        final String xml = RATE_LIMITED.replace("<resultCode>22</resultCode>",
                "<resultCode>" + code + "</resultCode>");

        assertThat(adapter(xml).fetchTrades("11350", "202607"))
                .as("빈 목록을 주면 '거래 0건'으로 저장되고 그 달은 영영 구멍이 된다")
                .isNull();
    }

    @Test
    @DisplayName("정상인데 0건이면 0건으로 저장한다 — 그건 실패가 아니다")
    void keepsAGenuineZero() {
        final List<ReferenceTrade> trades = adapter(OK_EMPTY).fetchTrades("11350", "202607");

        assertThat(trades)
                .as("진짜 0건까지 못 받은 것으로 보면 매번 다시 부른다")
                .isNotNull()
                .isEmpty();
    }

    @Test
    @DisplayName("정상이면 그대로 읽는다")
    void readsANormalResponse() {
        assertThat(adapter(OK_ONE).fetchTrades("11350", "202607"))
                .hasSize(1)
                .allSatisfy(t -> assertThat(t.apartmentName()).isEqualTo("송산"));
    }

    /**
     * 헤더를 안 주는 응답 형태가 있을 수 있습니다. <b>없다고 실패로 몰면</b>
     * 멀쩡한 달까지 안 받게 됩니다.
     */
    /**
     * <b>법정동과 번지는 이미 오고 있었습니다</b> (설계 I257). 버리고 있었을 뿐입니다 —
     * 이름이 통째로 바뀐 단지를 이것으로 잡습니다.
     */
    @Test
    @DisplayName("법정동과 번지를 읽는다 — 안 읽으면 이름으로만 가리게 된다")
    void readsTheLotFields() {
        final ReferenceTrade trade = adapter(OK_ONE).fetchTrades("11350", "202607").getFirst();

        assertThat(trade.legalDong()).isEqualTo("정릉동");
        assertThat(trade.jibun()).isEqualTo("1037");
        assertThat(trade.lot()).get()
                .isEqualTo(new banghak.home.halley.domain.property.JibunAddress("정릉동", 1037, 0));
    }

    /**
     * {@code jibun} 을 안 주는 응답이 있어 {@code bonbun}·{@code bubun} 으로 되짚습니다.
     * 네 자리 0채움이라 그대로 쓰면 {@code 0138} 이 됩니다.
     */
    @Test
    @DisplayName("jibun 이 없으면 bonbun·bubun 으로 되짚는다")
    void fallsBackToBonbunAndBubun() {
        final String withoutJibun = """
                <response><header><resultCode>00</resultCode></header><body><items>
                  <item><aptNm>송산</aptNm><excluUseAr>58.59</excluUseAr>
                        <dealAmount> 70,000</dealAmount><dealYear>2026</dealYear>
                        <dealMonth>7</dealMonth><dealDay>14</dealDay><floor>18</floor>
                        <umdNm>안암동3가</umdNm><bonbun>0138</bonbun><bubun>0002</bubun></item>
                </items></body></response>
                """;

        final ReferenceTrade trade = adapter(withoutJibun).fetchTrades("11350", "202607").getFirst();

        assertThat(trade.jibun()).as("0채움을 그대로 쓰면 0138 이 된다").isEqualTo("138-2");
        assertThat(trade.lot()).get()
                .isEqualTo(new banghak.home.halley.domain.property.JibunAddress("안암동3가", 138, 2));
    }

    @Test
    @DisplayName("부번이 0이면 본번만 쓴다")
    void dropsAZeroSubLot() {
        final String zeroBubun = """
                <response><header><resultCode>00</resultCode></header><body><items>
                  <item><aptNm>송산</aptNm><excluUseAr>58.59</excluUseAr>
                        <dealAmount> 70,000</dealAmount><dealYear>2026</dealYear>
                        <dealMonth>7</dealMonth><dealDay>14</dealDay><floor>18</floor>
                        <umdNm>정릉동</umdNm><bonbun>1037</bonbun><bubun>0000</bubun></item>
                </items></body></response>
                """;

        assertThat(adapter(zeroBubun).fetchTrades("11350", "202607").getFirst().jibun())
                .isEqualTo("1037");
    }

    @Test
    @DisplayName("코드가 아예 없으면 통과시킨다")
    void passesWhenThereIsNoCode() {
        final String noHeader = """
                <response><body><items/><totalCount>0</totalCount></body></response>
                """;

        assertThat(adapter(noHeader).fetchTrades("11350", "202607")).isNotNull();
    }

    @Test
    @DisplayName("CDATA 로 감싸 와도 읽는다")
    void readsCdataCodes() {
        final String cdata = """
                <response><header>
                  <resultCode><![CDATA[22]]></resultCode>
                  <resultMsg><![CDATA[LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR]]></resultMsg>
                </header></response>
                """;

        assertThat(adapter(cdata).fetchTrades("11350", "202607")).isNull();
    }

    @Test
    @DisplayName("전세 조회도 같다")
    void appliesToJeonseToo() {
        assertThat(adapter(RATE_LIMITED).fetchJeonseDeposits("11350", "202607")).isNull();
    }

    private MinistryReferenceAdapter adapter(String xml) {
        final MinistryReferenceFeignClient client = mock(MinistryReferenceFeignClient.class);
        given(client.fetchTrade(anyString(), anyString(), anyString(), anyInt())).willReturn(xml);
        given(client.fetchRent(anyString(), anyString(), anyString(), anyInt())).willReturn(xml);
        final RateGate gate = mock(RateGate.class);
        return new MinistryReferenceAdapter(client, gate, "key");
    }
}
