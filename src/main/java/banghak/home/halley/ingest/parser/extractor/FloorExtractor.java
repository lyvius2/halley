package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FloorExtractor implements FieldExtractor<String> {

    private static final Pattern FLOOR = Pattern.compile("(\\d+)\\s*[층/]\\s*(\\d+)");

    @Override
    public String key() {
        return "floor";
    }

    @Override
    public ParseResult<String> extract(TextDocument doc) {
        final Optional<String> value = doc.valueAfter("해당층/총층");
        if (value.isEmpty()) {
            return ParseResult.missing();
        }
        final Matcher matcher = FLOOR.matcher(value.get());
        if (!matcher.find()) {
            return ParseResult.missing();
        }
        return ParseResult.of(matcher.group(1) + "/" + matcher.group(2), "해당층/총층: " + value.get());
    }
}
