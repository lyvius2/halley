package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 배정 초등학교명 — 블록에서 "초등학교/중학교"로 끝나는 이름을 추출한다.
 * UI 문구("상세내용 숨기기" 등)는 건너뛴다.
 */
public class SchoolExtractor implements FieldExtractor<String> {

    private static final Pattern SCHOOL = Pattern.compile("([^\\s]*?초등학교|[^\\s]*?중학교)");
    private static final int SCAN_LINES = 12;

    private final String key;
    private final String[] labels;

    public SchoolExtractor(String key, String... labels) {
        this.key = key;
        this.labels = labels;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public ParseResult<String> extract(TextDocument doc) {
        for (final String label : labels) {
            final List<String> block = doc.linesAfterUntil(label, FieldLabels.SECTION_STOPS, SCAN_LINES);
            for (final String line : block) {
                final Matcher matcher = SCHOOL.matcher(line);
                if (matcher.find()) {
                    return ParseResult.of(matcher.group(1), label + ": " + line);
                }
            }
        }
        return ParseResult.missing();
    }
}
