package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.List;
import java.util.Optional;

public class LabelValueExtractor implements FieldExtractor<String> {

    private final String key;
    private final List<String> labels;

    public LabelValueExtractor(String key, String... labels) {
        this.key = key;
        this.labels = List.of(labels);
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public ParseResult<String> extract(TextDocument doc) {
        for (final String label : labels) {
            final Optional<String> value = doc.valueAfter(label);
            if (value.isPresent()) {
                return ParseResult.of(value.get(), label + ": " + value.get());
            }
        }
        return ParseResult.missing();
    }
}
