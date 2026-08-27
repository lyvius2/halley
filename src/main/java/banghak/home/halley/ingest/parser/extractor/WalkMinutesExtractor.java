package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 도보시간(N분) — 라벨 다음 블록(최대 12줄)에서 "N분"들을 찾아 최솟값(가장 가까운 곳)을 취한다.
 * 지하철·배정 초등학교처럼 여러 줄로 표기되는 경우에 대응.
 */
public class WalkMinutesExtractor implements FieldExtractor<Integer> {

    private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*분");
    private static final int SCAN_LINES = 12;

    private final String key;
    private final String[] labels;

    public WalkMinutesExtractor(String key, String... labels) {
        this.key = key;
        this.labels = labels;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public ParseResult<Integer> extract(TextDocument doc) {
        for (final String label : labels) {
            final List<String> block = doc.linesAfterUntil(label, FieldLabels.SECTION_STOPS, SCAN_LINES);
            if (block.isEmpty()) {
                continue;
            }
            final Integer minutes = minMinutes(block);
            if (minutes != null) {
                return ParseResult.of(minutes, label + " 도보시간");
            }
        }
        return ParseResult.missing();
    }

    private Integer minMinutes(List<String> block) {
        Integer min = null;
        for (final String line : block) {
            final Matcher matcher = MINUTES.matcher(line);
            while (matcher.find()) {
                final int value = Integer.parseInt(matcher.group(1));
                min = min == null ? value : Math.min(min, value);
            }
        }
        return min;
    }
}
