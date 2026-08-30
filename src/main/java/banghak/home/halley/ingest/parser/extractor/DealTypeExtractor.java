package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

public class DealTypeExtractor implements FieldExtractor<String> {

    private static final String DEAL_TYPE_PATTERN = "(?m)^\\s*(매매|전세)(?:\\s|$)";

    @Override
    public String key() {
        return "dealType";
    }

    @Override
    public ParseResult<String> extract(TextDocument doc) {
        return doc.firstMatch(DEAL_TYPE_PATTERN)
                .map(type -> ParseResult.of(type, "거래유형: " + type))
                .orElseGet(ParseResult::missing);
    }
}
