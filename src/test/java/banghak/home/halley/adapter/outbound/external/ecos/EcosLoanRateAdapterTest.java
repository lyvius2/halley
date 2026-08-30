package banghak.home.halley.adapter.outbound.external.ecos;

import banghak.home.halley.domain.loan.RatePoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("한국은행 ECOS 응답 파싱 (설계 I116)")
class EcosLoanRateAdapterTest {

    private final EcosLoanRateAdapter adapter = new EcosLoanRateAdapter(
            mock(EcosFeignClient.class), new ObjectMapper(), "key", "121Y006", "BECBLA03");

    /** 실제 응답에서 가져온 모양. 한 통계 안에 여러 항목이 섞여 온다. */
    private static final String BODY = """
            {"StatisticSearch":{"list_total_count":95,"row":[
              {"STAT_CODE":"121Y006","ITEM_CODE1":"BECBLA01","ITEM_NAME1":"대출평균 1)",
               "UNIT_NAME":"연%","TIME":"202401","DATA_VALUE":"5.04"},
              {"STAT_CODE":"121Y006","ITEM_CODE1":"BECBLA02","ITEM_NAME1":"기업대출",
               "UNIT_NAME":"연리%","TIME":"202401","DATA_VALUE":"5.22"},
              {"STAT_CODE":"121Y006","ITEM_CODE1":"BECBLA03","ITEM_NAME1":"가계대출",
               "UNIT_NAME":"연%","TIME":"202401","DATA_VALUE":"4.82"},
              {"STAT_CODE":"121Y006","ITEM_CODE1":"BECBLA0302","ITEM_NAME1":"주택담보대출",
               "UNIT_NAME":"연%","TIME":"202401","DATA_VALUE":"4.20"},
              {"STAT_CODE":"121Y006","ITEM_CODE1":"BECBLA03","ITEM_NAME1":"가계대출",
               "UNIT_NAME":"연%","TIME":"202402","DATA_VALUE":"4.75"}
            ]}}""";

    @Test
    @DisplayName("가계대출 항목만 고른다 — 안 거르면 기업대출 금리가 섞여 들어온다")
    void picksOnlyHouseholdRows() {
        final List<RatePoint> points = adapter.parse(BODY, YearMonth.of(2024, 1), YearMonth.of(2024, 2));

        assertThat(points).hasSize(2);
        assertThat(points).extracting(RatePoint::month)
                .containsExactly(YearMonth.of(2024, 1), YearMonth.of(2024, 2));
    }

    @Test
    @DisplayName("퍼센트를 소수로 바꾼다 — 여기서 통일 안 하면 계산이 100배 어긋난다")
    void convertsPercentToDecimal() {
        final List<RatePoint> points = adapter.parse(BODY, YearMonth.of(2024, 1), YearMonth.of(2024, 2));

        // "4.82" 연% → 0.0482
        assertThat(points.getFirst().rate()).isEqualByComparingTo("0.0482");
    }

    @Test
    @DisplayName("인증 실패는 HTTP 200에 RESULT로 온다 — 예외가 아니라 본문을 봐야 한다")
    void treatsResultBlockAsFailure() {
        final String rejected = """
                {"RESULT":{"CODE":"INFO-100","MESSAGE":"인증키가 유효하지 않습니다."}}""";

        assertThat(adapter.parse(rejected, YearMonth.of(2024, 1), YearMonth.of(2024, 2))).isEmpty();
    }

    @Test
    @DisplayName("항목 코드가 틀리면 빈 목록이다 — 조용히 넘어가면 원인을 못 찾는다")
    void returnsEmptyWhenItemCodeDoesNotMatch() {
        final EcosLoanRateAdapter wrong = new EcosLoanRateAdapter(
                mock(EcosFeignClient.class), new ObjectMapper(), "key", "121Y006", "NOT_A_CODE");

        assertThat(wrong.parse(BODY, YearMonth.of(2024, 1), YearMonth.of(2024, 2))).isEmpty();
    }

    @Test
    @DisplayName("키나 항목 코드가 비면 호출하지 않는다")
    void disabledWithoutConfig() {
        assertThat(new EcosLoanRateAdapter(mock(EcosFeignClient.class), new ObjectMapper(),
                "", "121Y006", "BECBLA03").isEnabled()).isFalse();
        assertThat(new EcosLoanRateAdapter(mock(EcosFeignClient.class), new ObjectMapper(),
                "key", "121Y006", "").isEnabled()).isFalse();
    }
}
