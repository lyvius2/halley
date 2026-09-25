package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NameExtractor implements FieldExtractor<String> {

    @Override
    public String key() {
        return "name";
    }

    @Override
    public ParseResult<String> extract(TextDocument doc) {
        final Optional<String> labeled = doc.valueAfter("단지명");
        if (labeled.isPresent()) {
            return ParseResult.of(labeled.get(), "단지명: " + labeled.get());
        }
        final String first = doc.firstNonBlankLine();
        if (first.isEmpty()) {
            return ParseResult.missing();
        }
        return ParseResult.of(ListingTitle.name(first), "제목: " + first);
    }
}
