package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MoveInExtractor implements FieldExtractor<String> {

    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})년\\s*(\\d{1,2})월\\s*(초순|중순|하순)");

    @Override
    public String key() {
        return "moveIn";
    }

    @Override
    public ParseResult<String> extract(TextDocument doc) {
        final Optional<String> value = doc.valueAfter("입주가능일");
        if (value.isEmpty()) {
            return ParseResult.missing();
        }
        final String raw = value.get();
        if (raw.contains("즉시") || raw.contains("협의")) {
            return ParseResult.of(raw, "입주가능일: " + raw);
        }
        final String derivedDate = deriveDate(raw);
        if (derivedDate != null) {
            return ParseResult.derived(raw, "입주가능일: " + raw, derivedDate + "로 추정");
        }
        return ParseResult.of(raw, "입주가능일: " + raw);
    }

    private String deriveDate(String raw) {
        final Matcher matcher = DATE_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        final int day = switch (matcher.group(3)) {
            case "초순" -> 5;
            case "중순" -> 15;
            default -> 25;
        };
        return String.format("%04d-%02d-%02d",
                Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), day);
    }
}
