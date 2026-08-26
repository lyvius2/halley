package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApprovalYearExtractor implements FieldExtractor<Integer> {

    private final String key;
    private final String label;

    public ApprovalYearExtractor(String key, String label) {
        this.key = key;
        this.label = label;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public ParseResult<Integer> extract(TextDocument doc) {
        final Optional<String> value = doc.valueAfter(label);
        if (value.isEmpty()) {
            return ParseResult.missing();
        }
        final Matcher matcher = Pattern.compile("(\\d{4})").matcher(value.get());
        if (!matcher.find()) {
            return ParseResult.missing();
        }
        return ParseResult.of(Integer.parseInt(matcher.group(1)), label + ": " + value.get());
    }
}
