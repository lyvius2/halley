package banghak.home.halley.adapter.outbound.external.building;

import banghak.home.halley.domain.building.BuildingLedger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("건축물대장 어댑터 (설계 I132)")
class BuildingLedgerAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 실호출로 받은 응답 그대로 — 동탄역시범호반써밋 (PNU 4159710500105250000). */
    private static final String REAL = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},
             "body":{"items":{"item":[{"rnum":1,
              "platPlc":"경기도 화성시 동탄구 청계동 525번지",
              "sigunguCd":"41597","bjdongCd":"10500","platGbCd":"0","bun":"0525","ji":"0000",
              "regstrKindCdNm":"총괄표제부","bldNm":"동탄역시범호반써밋",
              "platArea":64303,"archArea":10102.0863,"bcRat":15.71,
              "totArea":159638.1208,"vlRatEstmTotArea":111465.7649,"vlRat":173.34,
              "mainPurpsCdNm":"공동주택","hhldCnt":1002,"mainBldCnt":16,
              "totPkngCnt":1328,"useAprDay":"20150212",
              "engrRat":0,"engrEpi":0}]},
             "numOfRows":"1","pageNo":"1","totalCount":"1"}}}
            """;

    private BuildingLedgerAdapter adapter(BuildingLedgerFeignClient client, String key) {
        return new BuildingLedgerAdapter(client, objectMapper, key);
    }

    @Test
    @DisplayName("실호출 응답에서 용적률·대지면적·연식을 읽는다")
    void parsesRealResponse() {
        final BuildingLedger ledger = adapter(mock(BuildingLedgerFeignClient.class), "k")
                .parse(REAL, "4159710500105250000").orElseThrow();

        assertThat(ledger.buildingName()).isEqualTo("동탄역시범호반써밋");
        // 대장이 계산해 둔 값을 그대로 쓴다 (111465.7649 / 64303 × 100 = 173.34)
        assertThat(ledger.floorAreaRatio()).isEqualByComparingTo("173.34");
        assertThat(ledger.landArea()).isEqualByComparingTo("64303");
        assertThat(ledger.buildingCoverageRatio()).isEqualByComparingTo("15.71");
        assertThat(ledger.householdCount()).isEqualTo(1002);
        assertThat(ledger.mainBuildingCount()).isEqualTo(16);
        assertThat(ledger.parkingCount()).isEqualTo(1328);
        assertThat(ledger.approvedOn()).isEqualTo(LocalDate.of(2015, 2, 12));
    }

    @Test
    @DisplayName("연식을 센다 — 용적률 여유는 연식과 함께 봐야 의미가 있다")
    void computesAge() {
        final BuildingLedger ledger = adapter(mock(BuildingLedgerFeignClient.class), "k")
                .parse(REAL, "4159710500105250000").orElseThrow();

        assertThat(ledger.ageYears(LocalDate.of(2026, 2, 11))).isEqualTo(10);
        assertThat(ledger.ageYears(LocalDate.of(2026, 2, 12))).isEqualTo(11);
    }

    @Test
    @DisplayName("PNU를 쪼개 넘긴다 — platGbCd는 대장 체계로 바꾼다")
    void splitsPnuAndConvertsPlatGbCd() {
        final AtomicReference<String[]> sent = new AtomicReference<>();
        final BuildingLedgerFeignClient client =
                (key, sigungu, bjdong, platGb, bun, ji, type, rows) -> {
                    sent.set(new String[]{sigungu, bjdong, platGb, bun, ji, type});
                    return REAL;
                };

        adapter(client, "k").fetchRecapTitle("4159710500105250000");

        // PNU 11번째 자리가 '1'(대지) → 대장은 '0'
        assertThat(sent.get()).containsExactly("41597", "10500", "0", "0525", "0000", "json");
    }

    @Test
    @DisplayName("산(山) 필지는 platGbCd가 1이다 — PNU의 2와 코드 체계가 다르다")
    void convertsMountainParcel() {
        final AtomicReference<String> platGb = new AtomicReference<>();
        final BuildingLedgerFeignClient client =
                (key, sigungu, bjdong, gb, bun, ji, type, rows) -> {
                    platGb.set(gb);
                    return REAL;
                };

        // PNU 11번째가 '2' = 산
        adapter(client, "k").fetchRecapTitle("4159710500205250000");

        assertThat(platGb.get()).isEqualTo("1");
    }

    @Test
    @DisplayName("인증 실패는 HTTP 200에 resultCode로 온다")
    void treatsErrorCodeAsFailure() {
        final String rejected = """
                {"response":{"header":{"resultCode":"30",
                 "resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}}""";

        assertThat(adapter(mock(BuildingLedgerFeignClient.class), "k")
                .parse(rejected, "4159710500105250000")).isEmpty();
    }

    @Test
    @DisplayName("대장이 없는 필지는 비어 있다")
    void emptyWhenNoLedger() {
        final String none = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},
                 "body":{"items":{"item":[]},"totalCount":"0"}}}""";

        assertThat(adapter(mock(BuildingLedgerFeignClient.class), "k")
                .parse(none, "4159710500105250000")).isEmpty();
    }

    @Test
    @DisplayName("결과가 1건일 때 객체로 와도 읽는다")
    void handlesSingleObjectItem() {
        final String single = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},
                 "body":{"items":{"item":{"bldNm":"단독대장","vlRat":210.5,"platArea":3000,
                  "hhldCnt":50,"useAprDay":"19900101"}},"totalCount":"1"}}}""";

        final Optional<BuildingLedger> ledger = adapter(mock(BuildingLedgerFeignClient.class), "k")
                .parse(single, "4159710500105250000");

        assertThat(ledger).isPresent();
    }

    @Test
    @DisplayName("0으로 오는 칸은 '값 없음'으로 본다 — 용적률 0은 있을 수 없다")
    void treatsZeroAsMissing() {
        final String zeroed = REAL.replace("\"vlRat\":173.34", "\"vlRat\":0");

        assertThat(adapter(mock(BuildingLedgerFeignClient.class), "k")
                .parse(zeroed, "4159710500105250000").orElseThrow().floorAreaRatio()).isNull();
    }

    @Test
    @DisplayName("키가 없거나 PNU가 이상하면 부르지 않는다")
    void skipsWithoutKeyOrPnu() {
        final BuildingLedgerFeignClient boom = (k, a, b, c, d, e, f, g) -> {
            throw new AssertionError("불리면 안 된다");
        };

        assertThat(adapter(boom, "").fetchRecapTitle("4159710500105250000")).isEmpty();
        assertThat(adapter(boom, "k").fetchRecapTitle(null)).isEmpty();
        assertThat(adapter(boom, "k").fetchRecapTitle("41597")).isEmpty();
    }
}
