package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AreaValueExtractor implements FieldExtractor<BigDecimal> {

    private final String key;
    private final String label;

    public AreaValueExtractor(String key, String label) {
        this.key = key;
        this.label = label;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public ParseResult<BigDecimal> extract(TextDocument doc) {
        final Optional<String> value = doc.valueAfter(label);
        if (value.isEmpty()) {
            return ParseResult.missing();
        }
        final Matcher matcher = Pattern.compile("([\\d.]+)").matcher(value.get());
        if (!matcher.find()) {
            return ParseResult.missing();
        }
        return ParseResult.of(new BigDecimal(matcher.group(1)), label + ": " + value.get());
    }
}
