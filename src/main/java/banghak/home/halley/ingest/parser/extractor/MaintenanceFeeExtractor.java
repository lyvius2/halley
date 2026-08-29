package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.money.WonConverter;
import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaintenanceFeeExtractor implements FieldExtractor<Integer> {

    private static final Pattern PER_MONTH = Pattern.compile("([\\d,]+)\\s*만원");

    @Override
    public String key() {
        return "maintenanceFee";
    }

    @Override
    public ParseResult<Integer> extract(TextDocument doc) {
        // 상단 요약(18만원)보다 하단 상세의 '월 평균'(23만 4,762원)이 실제에 가깝다 — 설계 I53
        final Optional<String> average = doc.valueAfter("월 평균");
        if (average.isPresent()) {
            final Long won = WonConverter.toWon(average.get());
            if (won != null) {
                return ParseResult.derived(won.intValue(), "월 평균: " + average.get(),
                        "관리비 상세의 월 평균값을 사용");
            }
        }
        final Optional<String> value = doc.valueAfter("관리비");
        if (value.isEmpty()) {
            return ParseResult.missing();
        }
        final Matcher matcher = PER_MONTH.matcher(value.get());
        if (!matcher.find()) {
            return ParseResult.missing();
        }
        return ParseResult.of(Integer.parseInt(matcher.group(1).replace(",", "")) * 10_000, "관리비: " + value.get());
    }
}
