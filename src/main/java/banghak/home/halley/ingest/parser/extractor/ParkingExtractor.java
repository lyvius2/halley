package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParkingExtractor implements FieldExtractor<BigDecimal> {

    private static final Pattern PER_HOUSEHOLD = Pattern.compile("([\\d.]+)\\s*대");

    @Override
    public String key() {
        return "parkingPerHousehold";
    }

    @Override
    public ParseResult<BigDecimal> extract(TextDocument doc) {
        final Optional<String> value = doc.valueAfter("주차");
        if (value.isEmpty()) {
            return ParseResult.missing();
        }
        final Matcher matcher = PER_HOUSEHOLD.matcher(value.get());
        if (!matcher.find()) {
            return ParseResult.missing();
        }
        return ParseResult.of(new BigDecimal(matcher.group(1)), "주차: " + value.get());
    }
}
