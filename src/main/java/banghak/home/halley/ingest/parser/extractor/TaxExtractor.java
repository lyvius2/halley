package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.money.WonConverter;
import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

/**
 * 세금 항목 (설계 9.2 · I53) — 취득세 합계, 재산세 합계, 종합부동산세.
 * 금액 뒤에 `상세내역 보기` 같은 UI 문구가 붙어 오므로 금액 부분만 잘라낸다.
 */
public class TaxExtractor implements FieldExtractor<Object> {

    public enum Target { ACQUISITION, PROPERTY, COMPREHENSIVE }

    private final String key;
    private final Target target;

    public TaxExtractor(String key, Target target) {
        this.key = key;
        this.target = target;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public ParseResult<Object> extract(TextDocument doc) {
        final TextDocument section = doc.after("세금");
        return switch (target) {
            case ACQUISITION -> amount(section, "취득세 합계");
            case PROPERTY -> amount(section, "재산세 합계");
            case COMPREHENSIVE -> section.valueAfter("종합부동산세")
                    .<ParseResult<Object>>map(v -> ParseResult.of(v, "종합부동산세: " + v))
                    .orElseGet(ParseResult::missing);
        };
    }

    private ParseResult<Object> amount(TextDocument section, String label) {
        return section.valueAfter(label)
                .map(v -> v.replaceAll("^약\\s*", ""))
                .map(v -> v.replaceAll("(상세내역 보기|상세보기).*$", "").trim())
                .<ParseResult<Object>>map(v -> ParseResult.of(WonConverter.toWon(v), label + ": " + v))
                .orElseGet(ParseResult::missing);
    }
}
