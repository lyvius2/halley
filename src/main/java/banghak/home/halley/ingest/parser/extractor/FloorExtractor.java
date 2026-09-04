package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 해당층/총층.
 *
 * <p>해당층은 숫자가 아니라 <b>밴드(저·중·고)</b>로 오기도 합니다 — `고/19층`.
 * 채점은 이미 밴드를 다루는데(AGENTS.md) 파서가 숫자만 받아 통째로 빠졌습니다 (설계 I283).
 */
public class FloorExtractor implements FieldExtractor<String> {

    private static final Pattern FLOOR = Pattern.compile("(\\d+|저|중|고)\\s*[층/]\\s*(\\d+)");

    @Override
    public String key() {
        return "floor";
    }

    @Override
    public ParseResult<String> extract(TextDocument doc) {
        final Optional<String> value = doc.valueAfter("해당층/총층");
        if (value.isEmpty()) {
            return ParseResult.missing();
        }
        final Matcher matcher = FLOOR.matcher(value.get());
        if (!matcher.find()) {
            return ParseResult.missing();
        }
        return ParseResult.of(matcher.group(1) + "/" + matcher.group(2), "해당층/총층: " + value.get());
    }
}
