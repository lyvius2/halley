package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.money.WonConverter;
import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.math.BigDecimal;

/** 중개보수 상한액과 상한 요율. */
public class BrokerageExtractor implements FieldExtractor<Object> {

    public enum Target { FEE, RATE }

    private final String key;
    private final Target target;

    public BrokerageExtractor(String key, Target target) {
        this.key = key;
        this.target = target;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public ParseResult<Object> extract(TextDocument doc) {
        final TextDocument section = doc.after("중개 보수");
        if (target == Target.RATE) {
            return section.valueAfter("상한 요율")
                    .map(v -> v.replace("%", "").trim())
                    .filter(v -> v.matches("\\d+(\\.\\d+)?"))
                    .<ParseResult<Object>>map(v -> ParseResult.of(new BigDecimal(v), "상한 요율: " + v + "%"))
                    .orElseGet(ParseResult::missing);
        }
        return section.firstMatch("^\\s*최대\\s*([\\d,억만원\\s]+?)\\s*\\(")
                .map(String::trim)
                .<ParseResult<Object>>map(raw -> ParseResult.of(WonConverter.toWon(raw), "중개 보수: 최대 " + raw))
                .orElseGet(ParseResult::missing);
    }
}
