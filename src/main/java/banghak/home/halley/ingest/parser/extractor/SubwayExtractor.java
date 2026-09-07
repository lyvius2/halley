package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 가까운 지하철역. */
public class SubwayExtractor implements FieldExtractor<String> {

 /** 줄 맨 앞의 역 이름. 노선("1호선…")·거리("581m도보 9분") 줄은 안 걸린다. */
    private static final Pattern STATION = Pattern.compile("^([가-힣A-Za-z0-9·\\-]+역)");

    private static final int SCAN_LINES = 12;
    private static final int MAX_STATIONS = 3;

    @Override
    public String key() {
        return "subway";
    }

    @Override
    public ParseResult<String> extract(TextDocument doc) {
        final List<String> block = doc.linesAfterUntil("지하철", FieldLabels.SECTION_STOPS, SCAN_LINES);
        final List<String> stations = new ArrayList<>();
        for (final String line : block) {
            final var matcher = STATION.matcher(line);
            if (matcher.find() && !stations.contains(matcher.group(1))) {
                stations.add(matcher.group(1));
            }
            if (stations.size() == MAX_STATIONS) {
                break;
            }
        }
        if (stations.isEmpty()) {
            return ParseResult.missing();
        }
        final String value = String.join("/", stations);
        return ParseResult.of(value, "지하철: " + value);
    }
}
