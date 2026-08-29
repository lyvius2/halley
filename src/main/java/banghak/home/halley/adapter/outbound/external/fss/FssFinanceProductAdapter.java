package banghak.home.halley.adapter.outbound.external.fss;

import banghak.home.halley.application.port.out.external.FinanceProductPort;
import banghak.home.halley.domain.finance.FinanceCompany;
import banghak.home.halley.domain.finance.FinanceGroup;
import banghak.home.halley.domain.finance.LoanProduct;
import banghak.home.halley.domain.finance.LoanProductType;
import banghak.home.halley.domain.finance.LoanRateOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 금감원 금융상품통합비교공시 어댑터 (설계 I77).
 *
 * <p><b>응답이 두 배열로 나뉘어 옵니다.</b> `baseList`는 상품의 기본정보, `optionList`는 금리
 * 옵션이고, 둘을 {@code (fin_co_no, fin_prdt_cd)}로 맞물려야 한 상품이 됩니다. 한 상품에
 * 옵션이 여러 개(담보유형 × 상환방식 × 금리유형)라 배열을 따로 주는 구조입니다.
 *
 * <p><b>오류도 HTTP 200으로 옵니다.</b> 본문 `result.err_cd`가 `000`이 아니면 실패입니다 —
 * 인증키 문제(010~013)와 호출 한도 초과(020)를 여기서만 구분할 수 있습니다.
 */
@Slf4j
@Component
public class FssFinanceProductAdapter implements FinanceProductPort {

    private static final String SUCCESS_CODE = "000";
    private static final String COMPANY_SERVICE = "companySearch";
    /** 은행 주담대가 200건 남짓이라 넉넉하다. 넘으면 경고를 남긴다. */
    private static final int MAX_PAGES = 20;

    private final FssFeignClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public FssFinanceProductAdapter(FssFeignClient client,
                                    ObjectMapper objectMapper,
                                    @Value("${fss.api-key:}") String apiKey) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public List<LoanProduct> fetchLoanProducts(LoanProductType type, FinanceGroup group) {
        final List<JsonNode> base = new ArrayList<>();
        final List<JsonNode> options = new ArrayList<>();
        if (!collect(type.path(), group, base, options)) {
            return List.of();
        }
        // 옵션을 상품 키로 묶어 두고 기본정보를 훑는다 — 상품마다 옵션을 다시 뒤지면 O(n²)이다
        final Map<String, List<LoanRateOption>> byProduct = new LinkedHashMap<>();
        for (final JsonNode option : options) {
            byProduct.computeIfAbsent(productKey(option), k -> new ArrayList<>())
                    .add(toRateOption(option));
        }
        final List<LoanProduct> products = new ArrayList<>();
        for (final JsonNode item : base) {
            products.add(new LoanProduct(
                    type, group,
                    text(item, "dcls_month"),
                    text(item, "fin_co_no"),
                    text(item, "kor_co_nm"),
                    text(item, "fin_prdt_cd"),
                    text(item, "fin_prdt_nm"),
                    text(item, "join_way"),
                    text(item, "loan_inci_expn"),
                    text(item, "erly_rpay_fee"),
                    text(item, "dly_rate"),
                    text(item, "loan_lmt"),
                    byProduct.getOrDefault(productKey(item), List.of())));
        }
        log.info("FSS loan products fetched. type={}, group={}, products={}, options={}",
                type, group, products.size(), options.size());
        return products;
    }

    @Override
    public List<FinanceCompany> fetchCompanies(FinanceGroup group) {
        final List<JsonNode> base = new ArrayList<>();
        final List<JsonNode> options = new ArrayList<>();
        if (!collect(COMPANY_SERVICE, group, base, options)) {
            return List.of();
        }
        // 회사의 영업지역은 optionList에 회사코드별로 여러 줄 온다
        final Map<String, List<String>> areas = new LinkedHashMap<>();
        for (final JsonNode option : options) {
            final String name = text(option, "area_nm");
            if (name != null) {
                areas.computeIfAbsent(text(option, "fin_co_no"), k -> new ArrayList<>()).add(name);
            }
        }
        final List<FinanceCompany> companies = new ArrayList<>();
        for (final JsonNode item : base) {
            final String finCoNo = text(item, "fin_co_no");
            companies.add(new FinanceCompany(
                    group,
                    text(item, "dcls_month"),
                    finCoNo,
                    text(item, "kor_co_nm"),
                    text(item, "homp_url"),
                    text(item, "cal_tel"),
                    areas.getOrDefault(finCoNo, List.of())));
        }
        log.info("FSS companies fetched. group={}, companies={}", group, companies.size());
        return companies;
    }

    /**
     * 페이지를 끝까지 넘기며 두 배열을 모은다.
     *
     * @return 한 번이라도 성공했으면 true. 첫 페이지부터 실패하면 false — 빈 결과와
     *         '조회 실패'는 다른 상황이라 호출자가 구분할 수 있어야 한다
     */
    private boolean collect(String service, FinanceGroup group,
                            List<JsonNode> base, List<JsonNode> options) {
        if (!isEnabled()) {
            // 키가 없어 안 부른 것과 불렀는데 실패한 것은 다르다
            log.info("Skipping FSS lookup - fss.api-key not configured. service={}", service);
            return false;
        }
        int maxPage = 1;
        for (int page = 1; page <= maxPage && page <= MAX_PAGES; page++) {
            final String body = client.search(service, apiKey, group.code(), page);
            if (body == null) {
                return page > 1;
            }
            final JsonNode result = resultOf(body, service, group);
            if (result == null) {
                return page > 1;
            }
            result.path("baseList").forEach(base::add);
            result.path("optionList").forEach(options::add);
            maxPage = result.path("max_page_no").asInt(1);
        }
        if (maxPage > MAX_PAGES) {
            log.warn("FSS response truncated - some products are missing. "
                    + "service={}, group={}, maxPageNo={}, fetched={}", service, group, maxPage, MAX_PAGES);
        }
        return true;
    }

    /** 오류가 HTTP 200 + `err_cd`로 오므로 본문을 봐야 실패를 안다. */
    private JsonNode resultOf(String body, String service, FinanceGroup group) {
        try {
            final JsonNode result = objectMapper.readTree(body).path("result");
            final String errCd = result.path("err_cd").asString(null);
            if (errCd != null && !SUCCESS_CODE.equals(errCd)) {
                log.warn("FSS returned an error. service={}, group={}, errCd={}, errMsg={}",
                        service, group, errCd, result.path("err_msg").asString(null));
                return null;
            }
            return result;
        } catch (RuntimeException e) {
            log.warn("Failed to parse FSS response. service={}, group={}, cause={}",
                    service, group, e.toString());
            return null;
        }
    }

    private LoanRateOption toRateOption(JsonNode option) {
        return new LoanRateOption(
                text(option, "mrtg_type"),
                text(option, "mrtg_type_nm"),
                text(option, "rpay_type"),
                text(option, "rpay_type_nm"),
                text(option, "lend_rate_type"),
                text(option, "lend_rate_type_nm"),
                decimal(option, "lend_rate_min"),
                decimal(option, "lend_rate_max"),
                decimal(option, "lend_rate_avg"));
    }

    /** 상품은 회사코드만으로는 유일하지 않다 — 상품코드까지 합쳐야 한다. */
    private String productKey(JsonNode node) {
        return text(node, "fin_co_no") + "|" + text(node, "fin_prdt_cd");
    }

    private String text(JsonNode node, String field) {
        final String value = node.path(field).asString(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        final String value = text(node, field);
        try {
            return value == null ? null : new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
