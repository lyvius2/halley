package banghak.home.halley.adapter.outbound.external.fss;

import banghak.home.halley.domain.finance.FinanceCompany;
import banghak.home.halley.domain.finance.FinanceGroup;
import banghak.home.halley.domain.finance.LoanProduct;
import banghak.home.halley.domain.finance.LoanProductType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("금감원 금융상품 공시 응답 파싱 (설계 I77)")
class FssFinanceProductAdapterTest {

    /** 스펙 예시와 같은 모양 — 한 상품에 옵션이 둘이다. */
    private static final String MORTGAGE = """
            {"result": {"err_cd": "000", "err_msg": "정상", "total_count": 1,
              "max_page_no": 1, "now_page_no": 1,
              "baseList": [
                {"dcls_month": "202601", "fin_co_no": "0010001", "kor_co_nm": "우리은행",
                 "fin_prdt_cd": "WR0001", "fin_prdt_nm": "우리아파트론", "join_way": "영업점,인터넷",
                 "loan_inci_expn": "인지세 : 대출금액 50%",
                 "erly_rpay_fee": "대출금액 × 1.4% × 잔존일수/대출기간",
                 "dly_rate": "연체이자율 : 대출이자율 + 3%", "loan_lmt": "LTV 70% 이내"}],
              "optionList": [
                {"fin_co_no": "0010001", "fin_prdt_cd": "WR0001",
                 "mrtg_type": "A", "mrtg_type_nm": "아파트",
                 "rpay_type": "D", "rpay_type_nm": "분할상환방식",
                 "lend_rate_type": "F", "lend_rate_type_nm": "고정금리",
                 "lend_rate_min": "3.15", "lend_rate_max": "4.19", "lend_rate_avg": "3.62"},
                {"fin_co_no": "0010001", "fin_prdt_cd": "WR0001",
                 "mrtg_type": "A", "mrtg_type_nm": "아파트",
                 "rpay_type": "D", "rpay_type_nm": "분할상환방식",
                 "lend_rate_type": "C", "lend_rate_type_nm": "변동금리",
                 "lend_rate_min": "2.98", "lend_rate_max": "3.88", "lend_rate_avg": "3.21"}]}}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("baseList와 optionList를 회사코드+상품코드로 맞물려 한 상품으로 만든다")
    void joinsBaseAndOptionLists() {
        // given
        final FssFinanceProductAdapter adapter = adapterReturning(MORTGAGE);

        // when
        final List<LoanProduct> products =
                adapter.fetchLoanProducts(LoanProductType.MORTGAGE, FinanceGroup.BANK);

        // then
        assertThat(products).singleElement().satisfies(p -> {
            assertThat(p.companyName()).isEqualTo("우리은행");
            assertThat(p.productName()).isEqualTo("우리아파트론");
            assertThat(p.loanLimit()).isEqualTo("LTV 70% 이내");
            assertThat(p.options()).hasSize(2);
        });
    }

    @Test
    @DisplayName("가장 싼 옵션은 평균금리 기준으로 고른다 — 최저·최고는 조건부라 감각과 멀다")
    void picksCheapestByAverageRate() {
        // when
        final LoanProduct product = adapterReturning(MORTGAGE)
                .fetchLoanProducts(LoanProductType.MORTGAGE, FinanceGroup.BANK).getFirst();

        // then — 변동금리 3.21이 고정금리 3.62보다 싸다
        assertThat(product.cheapestOption()).isPresent().get()
                .satisfies(o -> {
                    assertThat(o.rateTypeName()).isEqualTo("변동금리");
                    assertThat(o.rateAvg()).isEqualByComparingTo("3.21");
                });
    }

    @Test
    @DisplayName("전세자금대출은 담보유형이 없어 null로 남는다")
    void jeonseHasNoMortgageType() {
        // given — 스펙상 rentHouseLoanProductsSearch의 옵션에는 mrtg_type이 없다
        final String jeonse = """
                {"result": {"err_cd": "000", "max_page_no": 1,
                  "baseList": [{"fin_co_no": "0010001", "fin_prdt_cd": "J1",
                                "kor_co_nm": "우리은행", "fin_prdt_nm": "우리전세론"}],
                  "optionList": [{"fin_co_no": "0010001", "fin_prdt_cd": "J1",
                                  "rpay_type_nm": "만기일시상환", "lend_rate_type_nm": "변동금리",
                                  "lend_rate_avg": "3.90"}]}}
                """;

        // when
        final LoanProduct product = adapterReturning(jeonse)
                .fetchLoanProducts(LoanProductType.JEONSE, FinanceGroup.BANK).getFirst();

        // then
        assertThat(product.options()).singleElement()
                .satisfies(o -> assertThat(o.mortgageType()).isNull());
    }

    @Test
    @DisplayName("오류는 HTTP 200 + err_cd로 오므로 본문을 보고 걸러낸다")
    void rejectsErrorCode() {
        // given — 010 미등록 인증키
        final String error = """
                {"result": {"err_cd": "010", "err_msg": "등록되지 않은 인증키입니다."}}
                """;

        // when
        final List<LoanProduct> products = adapterReturning(error)
                .fetchLoanProducts(LoanProductType.MORTGAGE, FinanceGroup.BANK);

        // then
        assertThat(products).isEmpty();
    }

    @Test
    @DisplayName("인증키가 없으면 호출하지 않는다")
    void skipsWithoutApiKey() {
        // given
        final RecordingClient client = new RecordingClient(MORTGAGE);
        final FssFinanceProductAdapter adapter =
                new FssFinanceProductAdapter(client, objectMapper, "");

        // when
        final List<LoanProduct> products =
                adapter.fetchLoanProducts(LoanProductType.MORTGAGE, FinanceGroup.BANK);

        // then
        assertThat(products).isEmpty();
        assertThat(client.calls).isZero();
    }

    @Test
    @DisplayName("금융회사의 영업지역은 optionList에서 회사별로 모은다")
    void collectsCompanyAreas() {
        // given
        final String companies = """
                {"result": {"err_cd": "000", "max_page_no": 1,
                  "baseList": [{"fin_co_no": "0013175", "kor_co_nm": "대백저축은행",
                                "dcls_month": "202601", "homp_url": "http://example.kr",
                                "cal_tel": "053-000-0000"}],
                  "optionList": [{"fin_co_no": "0013175", "area_cd": "027", "area_nm": "대구"},
                                 {"fin_co_no": "0013175", "area_cd": "026", "area_nm": "경북"}]}}
                """;

        // when
        final List<FinanceCompany> result =
                adapterReturning(companies).fetchCompanies(FinanceGroup.SAVINGS_BANK);

        // then
        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.name()).isEqualTo("대백저축은행");
            assertThat(c.callCenterTel()).isEqualTo("053-000-0000");
            assertThat(c.areas()).containsExactly("대구", "경북");
        });
    }

    private FssFinanceProductAdapter adapterReturning(String body) {
        return new FssFinanceProductAdapter(new RecordingClient(body), objectMapper, "dummy-key");
    }

    /** 호출 횟수를 세는 스텁 — 키가 없을 때 정말 안 부르는지 확인한다. */
    private static class RecordingClient implements FssFeignClient {

        int calls;
        private final String body;

        RecordingClient(String body) {
            this.body = body;
        }

        @Override
        public String search(String service, String auth, String topFinGrpNo, int pageNo) {
            calls++;
            return body;
        }
    }
}
