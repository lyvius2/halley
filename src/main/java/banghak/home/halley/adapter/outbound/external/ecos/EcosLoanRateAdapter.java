package banghak.home.halley.adapter.outbound.external.ecos;

import banghak.home.halley.application.port.out.external.LoanRateHistoryPort;
import banghak.home.halley.domain.loan.RatePoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 한국은행 ECOS 어댑터 (설계 I116).
 *
 * <p>통계 `121Y006`(예금은행 대출금리, 신규취급액 기준)에서 가계대출 항목만 골라 옵니다.
 * 한 통계 안에 대출평균·기업대출·가계대표 등 여러 항목이 섞여 오므로 <b>`ITEM_CODE1`으로
 * 걸러야</b> 합니다 — 안 거르면 기업대출 금리가 섞여 들어옵니다.
 *
 * <p><b>항목 코드를 프로퍼티로 둡니다.</b> 한국은행이 코드 체계를 바꾸면 코드 수정 없이
 * 설정만 고쳐 대응합니다.
 */
@Slf4j
@Component
public class EcosLoanRateAdapter implements LoanRateHistoryPort {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    /** 5년 = 60개월. 넉넉히 잡아도 한 번에 받는 편이 호출을 줄인다. */
    private static final int MAX_ROWS = 1000;
    /** ECOS는 퍼센트로 준다 (`UNIT_NAME: 연%`). 도메인은 소수로 통일한다. */
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    private final EcosFeignClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String statCode;
    private final String householdItemCode;

    public EcosLoanRateAdapter(EcosFeignClient client,
                               ObjectMapper objectMapper,
                               @Value("${ecos.api-key:}") String apiKey,
                               @Value("${ecos.stat-code:121Y006}") String statCode,
                               @Value("${ecos.item-code.household-loan:}") String householdItemCode) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.statCode = statCode;
        this.householdItemCode = householdItemCode;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank()
                && householdItemCode != null && !householdItemCode.isBlank();
    }

    @Override
    public List<RatePoint> fetchHouseholdLoanRates(YearMonth from, YearMonth to) {
        if (!isEnabled()) {
            // 어느 쪽이 비었는지 말해 준다. "안 나온다"의 원인이 키인지 코드인지 갈린다
            log.info("Skipping ECOS lookup - apiKeySet={}, itemCodeSet={}",
                    apiKey != null && !apiKey.isBlank(),
                    householdItemCode != null && !householdItemCode.isBlank());
            return List.of();
        }
        final String body = client.search(apiKey, 1, MAX_ROWS, statCode, "M",
                from.format(MONTH), to.format(MONTH));
        if (body == null) {
            return List.of();
        }
        return parse(body, from, to);
    }

    List<RatePoint> parse(String body, YearMonth from, YearMonth to) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (RuntimeException e) {
            log.warn("Failed to parse ECOS response. cause={}", e.getMessage());
            return List.of();
        }
        // 인증 실패·조회 없음도 HTTP 200으로 온다. 본문에 RESULT 가 실린다
        final JsonNode result = root.path("RESULT");
        if (!result.isMissingNode()) {
            log.warn("ECOS lookup rejected. code={}, message={}",
                    result.path("CODE").asString(null), result.path("MESSAGE").asString(null));
            return List.of();
        }
        final JsonNode rows = root.path("StatisticSearch").path("row");
        if (!rows.isArray() || rows.isEmpty()) {
            log.info("ECOS returned no rows. statCode={}, period={}~{}", statCode, from, to);
            return List.of();
        }
        final List<RatePoint> points = new ArrayList<>();
        for (final JsonNode row : rows) {
            if (!householdItemCode.equals(row.path("ITEM_CODE1").asString(null))) {
                continue;
            }
            final RatePoint point = toPoint(row);
            if (point != null) {
                points.add(point);
            }
        }
        if (points.isEmpty()) {
            // 항목 코드가 틀리면 여기로 온다 — 조용히 빈 목록을 주면 원인을 못 찾는다
            log.warn("ECOS returned rows but none matched the household item code. "
                            + "statCode={}, itemCode={}, rows={}",
                    statCode, householdItemCode, rows.size());
        } else {
            log.info("ECOS household loan rates loaded. period={}~{}, points={}", from, to, points.size());
        }
        return points;
    }

    private RatePoint toPoint(JsonNode row) {
        final String time = row.path("TIME").asString(null);
        final String value = row.path("DATA_VALUE").asString(null);
        if (time == null || value == null || value.isBlank()) {
            return null;
        }
        try {
            return new RatePoint(
                    YearMonth.parse(time, MONTH),
                    new BigDecimal(value.trim()).divide(PERCENT, 6, RoundingMode.HALF_UP));
        } catch (RuntimeException e) {
            log.warn("Skipping malformed ECOS row. time={}, value={}", time, value);
            return null;
        }
    }
}
