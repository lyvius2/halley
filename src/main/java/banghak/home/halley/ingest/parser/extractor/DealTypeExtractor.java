package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

/** 거래유형. */
public class DealTypeExtractor implements FieldExtractor<String> {

    private static final String DEAL_TYPE_PATTERN = "(?m)^\\s*(매매|전세)(?:\\s|$)";

    @Override
    public String key() {
        return "dealType";
    }

    @Override
    public ParseResult<String> extract(TextDocument doc) {
        final String title = doc.firstNonBlankLine();
        final var fromTitle = ListingTitle.dealType(title).filter(t -> !"월세".equals(t));
        if (fromTitle.isPresent()) {
            return ParseResult.of(fromTitle.get(), "제목: " + title);
        }
        if (doc.valueAfter("매매가").isPresent()) {
            return ParseResult.of("매매", "매매가 라벨");
        }
        if (doc.valueAfter("전세가").isPresent()) {
            return ParseResult.of("전세", "전세가 라벨");
        }
        return doc.firstMatch(DEAL_TYPE_PATTERN)
                .map(type -> ParseResult.of(type, "거래유형: " + type))
                .orElseGet(ParseResult::missing);
    }
}
