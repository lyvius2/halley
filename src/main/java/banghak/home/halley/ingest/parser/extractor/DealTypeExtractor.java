package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

/**
 * 거래유형 (설계 I283).
 *
 * <p><b>제목을 먼저 믿습니다.</b> 본문 아래쪽에는 "매매 / 전세 / 월세" 를 고르는
 * <b>토글 글자</b>가 그대로 붙어 옵니다 — 매매 매물인데 거기 걸려 <b>전세로 읽혔습니다.</b>
 * 매매와 전세는 순위표가 아예 달라(AGENTS.md) 조용히 틀리면 매물이 딴 표에 실립니다.
 */
public class DealTypeExtractor implements FieldExtractor<String> {

    private static final String DEAL_TYPE_PATTERN = "(?m)^\\s*(매매|전세)(?:\\s|$)";

    @Override
    public String key() {
        return "dealType";
    }

    @Override
    public ParseResult<String> extract(TextDocument doc) {
        final String title = doc.firstNonBlankLine();
        final var fromTitle = ListingTitle.dealType(title).filter(t -> !"월세".equals(t));
        if (fromTitle.isPresent()) {
            return ParseResult.of(fromTitle.get(), "제목: " + title);
        }
        // 값 라벨이 유형을 말해 준다 — "매매가" / "전세가"
        if (doc.valueAfter("매매가").isPresent()) {
            return ParseResult.of("매매", "매매가 라벨");
        }
        if (doc.valueAfter("전세가").isPresent()) {
            return ParseResult.of("전세", "전세가 라벨");
        }
        return doc.firstMatch(DEAL_TYPE_PATTERN)
                .map(type -> ParseResult.of(type, "거래유형: " + type))
                .orElseGet(ParseResult::missing);
    }
}
