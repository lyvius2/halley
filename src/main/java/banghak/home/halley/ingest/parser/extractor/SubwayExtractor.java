package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 가까운 지하철역.
 *
 * 블록은 역마다 세 줄로 옵니다 — 역 이름, 노선, 거리·도보시간.
 *
 *
 * 지하철
 *
 *     회기역
 *         1호선경의중앙경춘
 *         581m도보 9분
 *     외대앞역
 *         1호선
 *         894m도보 14분
 *
 *
 * 예전에는 라벨 바로 다음 줄만 값으로 읽었는데 그 줄이 비어 있어 통째로
 * 빠졌습니다. 역 이름만 모아 `/` 로 잇습니다 — 도보시간은 subwayMinutes 가
 * 따로 가집니다.
 */
public class SubwayExtractor implements FieldExtractor<String> {

    /**
     * 줄 맨 앞의 역 이름. 노선("1호선…")·거리("581m도보 9분") 줄은 안 걸린다.
     *
     * 뒤에 무엇이 붙어 오든 역까지만 끊습니다. 붙여넣기마다 모양이 다릅니다.
     *
     *
     * 회기역                ← 이름만
     * 마들역7호선            ← 노선이 공백 없이 붙는다
     * 노원역4호선7호선        ← 여럿 붙기도 한다
     * 독립문역 7분           ← 도보시간이 붙는다
     *
     *
     * 탐욕 반복이라 마지막 `역`까지 잡습니다 — `동대문역사문화공원역` 처럼
     * 이름 안에 `역`이 또 있어도 통째로 가져옵니다. 도보시간은 subwayMinutes 가
     * 따로 가지므로 이름에 섞지 않습니다.
     */
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
