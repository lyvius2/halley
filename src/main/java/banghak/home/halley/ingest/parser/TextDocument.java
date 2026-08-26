package banghak.home.halley.ingest.parser;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextDocument {

    private final String raw;
    private final List<String> lines;

    public TextDocument(String raw) {
        this.raw = raw;
        this.lines = List.of(raw.split("\\R"));
    }

    public String raw() {
        return raw;
    }

    /**
     * 라벨 라인의 다음 비어 있지 않은 라인 값을 반환한다. 네이버의 "라벨 \n 값" 구조에 대응.
     */
    public Optional<String> valueAfter(String label) {
        for (int i = 0; i < lines.size() - 1; i++) {
            if (lines.get(i).trim().equals(label)) {
                final String value = lines.get(i + 1).trim();
                if (!value.isEmpty()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 원문 전체에서 첫 정규식 그룹(1)을 반환한다. MULTILINE 필수.
     */
    public Optional<String> firstMatch(String regex) {
        final Matcher matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(raw);
        return matcher.find() ? Optional.ofNullable(matcher.group(1)) : Optional.empty();
    }
}
