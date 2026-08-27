package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WalkMinutesExtractor implements FieldExtractor<Integer> {

    private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*분");

    private final String key;
    private final String label;

    public WalkMinutesExtractor(String key, String label) {
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
        final Matcher matcher = MINUTES.matcher(value.get());
        if (!matcher.find()) {
            return ParseResult.missing();
        }
        return ParseResult.of(Integer.parseInt(matcher.group(1)), label + ": " + value.get());
    }
}
