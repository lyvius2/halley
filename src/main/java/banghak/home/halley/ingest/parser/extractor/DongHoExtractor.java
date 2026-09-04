package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.Optional;

/**
 * 동/호 — "동/호" 라벨이 있으면 그 값, 없으면 제목 첫 줄의 "N동(N호)" 부분.
 */
public class DongHoExtractor implements FieldExtractor<String> {

    @Override
    public String key() {
        return "dongHo";
    }

    @Override
    public ParseResult<String> extract(TextDocument doc) {
        final Optional<String> labeled = doc.valueAfter("동/호");
        if (labeled.isPresent()) {
            return ParseResult.of(labeled.get(), "동/호: " + labeled.get());
        }
        final String first = doc.firstNonBlankLine();
        if (first.isEmpty()) {
            return ParseResult.missing();
        }
        return ListingTitle.dongHo(first)
                .map(value -> ParseResult.of(value, "제목: " + first))
                .orElseGet(ParseResult::missing);
    }
}
