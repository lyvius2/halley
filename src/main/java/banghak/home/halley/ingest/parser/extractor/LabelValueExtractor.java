package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * 라벨 다음 줄을 값으로 읽는다.
 *
 * <p>{@code cleaner}로 표기 차이를 흡수합니다 — 같은 항목인데 페이지마다 `3/2`와 `3/2개`처럼
 * 다르게 오면 같은 값이 두 모양으로 저장됩니다 (설계 I82).
 */
public class LabelValueExtractor implements FieldExtractor<String> {

    private final String key;
    private final List<String> labels;
    private final UnaryOperator<String> cleaner;

    public LabelValueExtractor(String key, String... labels) {
        this(key, UnaryOperator.identity(), labels);
    }

    public LabelValueExtractor(String key, UnaryOperator<String> cleaner, String... labels) {
        this.key = key;
        this.cleaner = cleaner;
        this.labels = List.of(labels);
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public ParseResult<String> extract(TextDocument doc) {
        for (final String label : labels) {
            final Optional<String> value = doc.valueAfter(label).map(cleaner).filter(v -> !v.isBlank());
            if (value.isPresent()) {
                return ParseResult.of(value.get(), label + ": " + value.get());
            }
        }
        return ParseResult.missing();
    }
}
