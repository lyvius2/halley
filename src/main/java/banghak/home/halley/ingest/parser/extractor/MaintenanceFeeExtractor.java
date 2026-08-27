package banghak.home.halley.ingest.parser.extractor;

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
