package banghak.home.halley.domain.forecast;

import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.support.WonFormat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM에 보낼 프롬프트와 <b>그 안에 든 숫자들</b> (설계 I134).
 *
 * <p>숫자를 따로 들고 있는 이유는 <b>답을 검증하기 위해서</b>입니다. 모델이 인용한 숫자가
 * 여기 없으면 지어낸 것이므로 그 요인을 버립니다(2.2-A).
 *
 * <p><b>원본 거래 목록을 넣지 않습니다.</b> 이미 계산된 값만 넣습니다 —
 * 넣으면 모델이 산술을 하게 되고, 조용히 틀립니다.
 */
public record ForecastPrompt(String system, String user, Set<String> allowedNumbers) {

    /** 문장에서 숫자를 뽑는다. 천 단위 쉼표와 소수점을 포함한다. */
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    private static final String SYSTEM = """
            당신은 한국 부동산 가격 전망을 돕는 조력자입니다.
            아래에 주어진 지표는 이미 계산된 값입니다. 다시 계산하지 마세요.

            반드시 아래 JSON 형식으로만 답하세요. 다른 문장을 덧붙이지 마세요.
            {
              "direction": "UP | DOWN | FLAT | UNCERTAIN",
              "confidence": "LOW | MEDIUM | HIGH",
              "factors": [
                {"name": "<요인 이름>", "effect": "UP | DOWN | FLAT",
                 "weight": "HIGH | MEDIUM | LOW", "evidence": "<한국어 한 문장>"}
              ],
              "summary": "<한국어 두세 문장>",
              "caveats": ["<이 판단이 놓치고 있는 것>"]
            }

            판단 지침
            - direction은 향후 %d개월 동안 이 매물의 가격이 어느 쪽으로 움직일지입니다.
            - 재료가 모자라면 UNCERTAIN을 고르세요. 넷 중 하나를 억지로 고르지 마세요.
            - evidence에는 위에 주어진 숫자만 인용하세요. 없는 숫자를 지어내지 마세요.
            - evidence는 한 문장, summary는 두세 문장으로 줄이세요. 길게 쓰면 답이 잘립니다.
            - 지표들이 서로 다른 방향을 가리키면 그 사실을 summary에 밝히고 confidence를 낮추세요.
            - caveats에는 이 판단이 보지 못한 것을 적으세요. 정책 변화와 개별 단지의 수급은 알 수 없습니다.
            - 이것은 예측이며 틀릴 수 있습니다. 단정적인 표현을 쓰지 마세요.
            """;

    /**
     * @param factors 코드가 계산한 요인들. <b>코드의 종합 예측은 넣지 않습니다</b> —
     *                보여 주면 모델이 끌려가 두 예측이 독립이 아니게 됩니다 (설계 4.5)
     */
    public static ForecastPrompt of(Property property, List<PriceFactor> factors, int horizonMonths) {
        final StringJoiner sb = new StringJoiner("\n");
        sb.add("[매물]");
        sb.add("단지명: " + text(property.name()));
        sb.add("거래유형: " + (property.dealType() == null ? "정보 없음" : property.dealType().name()));
        sb.add("호가: " + won(property.priceDeposit()));
        sb.add("전용면적(㎡): " + number(property.areaExclusiveM2()));
        sb.add("해당층/총층: " + (property.floorNo() == null ? "정보 없음"
                : property.floorNo() + "/" + number(property.floorTotal())));
        sb.add("사용승인연도: " + number(property.approvalYear()));
        sb.add("세대수: " + number(property.totalHouseholds()));
        sb.add("지번주소: " + text(property.addressJibun()));
        sb.add("공시가격: " + won(property.officialPrice()));

        sb.add("");
        sb.add("[계산된 지표]  ※ 코드가 계산한 값이다. 다시 계산하지 마라");
        if (factors.isEmpty()) {
            sb.add("산출된 지표가 없습니다");
        } else {
            for (final PriceFactor factor : factors) {
                sb.add(String.format("- %s (%s) : %s",
                        factor.name(), factor.weight().label(), factor.evidence()));
            }
        }
        final String user = sb.toString();
        return new ForecastPrompt(String.format(SYSTEM, horizonMonths), user, numbersIn(user));
    }

    /**
     * 프롬프트에 실제로 등장한 숫자들.
     *
     * <p>모델이 이 밖의 숫자를 인용하면 <b>지어낸 것</b>입니다. 쉼표를 걷어 내고 담아
     * `12,000`과 `12000`을 같게 봅니다.
     */
    static Set<String> numbersIn(String text) {
        final Set<String> numbers = new LinkedHashSet<>();
        final Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            numbers.add(normalize(matcher.group()));
        }
        return numbers;
    }

    /**
     * 이 문장이 인용한 숫자가 전부 프롬프트에 있었는가.
     *
     * <p>한 자리 수는 봐줍니다 — "3개월"·"2건" 같은 말이 자연스럽게 섞이는데
     * 그것까지 막으면 멀쩡한 근거가 버려집니다. <b>지어낸 금액·비율을 잡는 것이 목적</b>입니다.
     */
    public boolean citesOnlyKnownNumbers(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return false;
        }
        final Matcher matcher = NUMBER.matcher(evidence);
        while (matcher.find()) {
            final String value = normalize(matcher.group());
            if (value.length() <= 1) {
                continue;
            }
            if (!allowedNumbers.contains(value)) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String value) {
        final String plain = value.replace(",", "");
        // 12.0 과 12 를 같게 본다 — 모델이 소수점을 붙이거나 떼는 일이 잦다
        return plain.contains(".") ? plain.replaceAll("0+$", "").replaceAll("\\.$", "") : plain;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "정보 없음" : value;
    }

    private static String won(Long amount) {
        return amount == null ? "정보 없음" : WonFormat.of(amount);
    }

    private static String number(Object value) {
        if (value == null) {
            return "정보 없음";
        }
        return value instanceof BigDecimal decimal
                ? decimal.stripTrailingZeros().toPlainString()
                : String.valueOf(value);
    }

    /** 프롬프트 전체 — 해시로 중복 호출을 막을 때 쓴다 (설계 I59). */
    public String full() {
        final List<String> parts = new ArrayList<>();
        parts.add(system);
        parts.add(user);
        return String.join("\n", parts);
    }
}
