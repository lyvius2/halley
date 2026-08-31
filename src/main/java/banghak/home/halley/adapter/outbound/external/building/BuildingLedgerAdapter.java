package banghak.home.halley.adapter.outbound.external.building;

import banghak.home.halley.application.port.out.external.BuildingLedgerPort;
import banghak.home.halley.domain.building.BuildingLedger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 건축물대장 어댑터 (설계 I132).
 *
 * <p>PNU 19자리를 쪼개 넣습니다.
 *
 * <pre>
 *   41597   10500   1   0525   0000
 *     │       │     │     │      └── ji        (부번 4)
 *     │       │     │     └───────── bun       (본번 4)
 *     │       │     └─────────────── 산 여부    → platGbCd 로 변환
 *     │       └───────────────────── bjdongCd  (법정동 5)
 *     └───────────────────────────── sigunguCd (시군구 5)
 * </pre>
 */
@Slf4j
@Component
public class BuildingLedgerAdapter implements BuildingLedgerPort {

    private static final int PNU_LENGTH = 19;
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 한 필지에 총괄표제부가 여럿일 이유는 없지만, 있으면 첫 건만 본다. */
    private static final int MAX_ROWS = 5;

    private final BuildingLedgerFeignClient client;
    private final ObjectMapper objectMapper;
    private final String serviceKey;

    public BuildingLedgerAdapter(BuildingLedgerFeignClient client,
                                 ObjectMapper objectMapper,
                                 @Value("${ministry.service-key:}") String serviceKey) {
        this.client = client;
        this.objectMapper = objectMapper;
        // 실거래가와 같은 키다. Encoding 키가 들어와도 동작하도록 되돌린다
        this.serviceKey = decodeIfEncoded(serviceKey);
    }

    /**
     * 공공데이터포털은 인증키를 Encoding/Decoding 두 형태로 발급합니다. Encoding 키를 그대로
     * 넘기면 Feign이 `%`를 한 번 더 인코딩해 403이 납니다 — 실거래가에서 겪은 것과 같습니다.
     */
    static String decodeIfEncoded(String key) {
        if (key == null || !key.contains("%")) {
            return key;
        }
        return URLDecoder.decode(key.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    @Override
    public boolean isEnabled() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    @Override
    public Optional<BuildingLedger> fetchRecapTitle(String pnu) {
        if (!isEnabled()) {
            log.info("Skipping building ledger lookup - service key not configured.");
            return Optional.empty();
        }
        if (pnu == null || pnu.length() != PNU_LENGTH) {
            log.info("Skipping building ledger lookup - invalid PNU. pnu={}", pnu);
            return Optional.empty();
        }
        final String body = client.fetchRecapTitle(serviceKey,
                pnu.substring(0, 5), pnu.substring(5, 10), platGbCd(pnu.charAt(10)),
                pnu.substring(11, 15), pnu.substring(15, 19), "json", MAX_ROWS);
        if (body == null) {
            return Optional.empty();
        }
        return parse(body, pnu);
    }

    /**
     * PNU의 산 여부를 건축물대장 코드로 바꾼다.
     *
     * <p><b>체계가 다릅니다.</b> PNU는 `1`=대지·`2`=산, 대장은 `0`=대지·`1`=산·`2`=블록.
     * 그대로 넘기면 <b>엉뚱한 필지</b>를 봅니다.
     */
    private String platGbCd(char pnuMountainFlag) {
        return pnuMountainFlag == '2' ? "1" : "0";
    }

    Optional<BuildingLedger> parse(String body, String pnu) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (RuntimeException e) {
            log.warn("Failed to parse building ledger response. pnu={}, cause={}", pnu, e.getMessage());
            return Optional.empty();
        }
        final JsonNode header = root.path("response").path("header");
        final String code = header.path("resultCode").asString(null);
        if (code != null && !"00".equals(code)) {
            // 인증 실패·미신청도 HTTP 200으로 온다. 본문을 봐야 안다
            log.warn("Building ledger lookup rejected. pnu={}, resultCode={}, resultMsg={}",
                    pnu, code, header.path("resultMsg").asString(null));
            return Optional.empty();
        }
        final JsonNode item = firstItem(root);
        if (item == null) {
            log.info("No building ledger for this parcel. pnu={}", pnu);
            return Optional.empty();
        }
        return Optional.of(new BuildingLedger(
                text(item, "bldNm"),
                decimal(item, "platArea"),
                decimal(item, "vlRat"),
                decimal(item, "bcRat"),
                integer(item, "hhldCnt"),
                integer(item, "mainBldCnt"),
                integer(item, "totPkngCnt"),
                date(item, "useAprDay")));
    }

    /**
     * 결과가 1건이면 배열이 아니라 <b>객체로 오는</b> 공공 API가 있습니다.
     * 실측에서는 배열이었지만 둘 다 받습니다.
     */
    private JsonNode firstItem(JsonNode root) {
        final JsonNode items = root.path("response").path("body").path("items").path("item");
        if (items.isArray()) {
            return items.isEmpty() ? null : items.get(0);
        }
        return items.isObject() ? items : null;
    }

    private String text(JsonNode item, String field) {
        final String value = item.path(field).asString(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 숫자로 올 수도 문자열로 올 수도 있습니다 — 실측에서 `numOfRows`가 문자열이었습니다. */
    private BigDecimal decimal(JsonNode item, String field) {
        final JsonNode node = item.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            final BigDecimal value = new BigDecimal(node.asString().trim());
            // 0은 '값이 없다'는 뜻으로 오는 칸이 있다 (engrRat 등). 용적률 0은 있을 수 없다
            return value.signum() == 0 ? null : value;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Integer integer(JsonNode item, String field) {
        final BigDecimal value = decimal(item, field);
        return value == null ? null : value.intValue();
    }

    private LocalDate date(JsonNode item, String field) {
        final String value = text(item, field);
        if (value == null || value.length() != 8) {
            return null;
        }
        try {
            return LocalDate.parse(value, YMD);
        } catch (RuntimeException e) {
            log.warn("Malformed date in building ledger. field={}, value={}", field, value);
            return null;
        }
    }
}
