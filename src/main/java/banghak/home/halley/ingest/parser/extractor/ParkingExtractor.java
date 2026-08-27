package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParkingExtractor implements FieldExtractor<BigDecimal> {

    private static final Pattern PER_HOUSEHOLD = Pattern.compile("세대당\\s*([\\d.]+)\\s*대");
    private static final Pattern ANY_RATIO = Pattern.compile("([\\d.]+)\\s*대");

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
        final Matcher per = PER_HOUSEHOLD.matcher(value.get());
        final Matcher fallback = ANY_RATIO.matcher(value.get());
        if (per.find()) {
            return ParseResult.of(new BigDecimal(per.group(1)), "주차: " + value.get());
        }
        if (fallback.find()) {
            return ParseResult.of(new BigDecimal(fallback.group(1)), "주차: " + value.get());
        }
        return ParseResult.missing();
    }
}
