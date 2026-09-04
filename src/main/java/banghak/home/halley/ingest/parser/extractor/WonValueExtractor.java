package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.money.WonConverter;
import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.List;
import java.util.Optional;

public class WonValueExtractor implements FieldExtractor<Long> {

    private final String key;
    private final boolean slashDeposit;
    private final List<String> labels;

    public WonValueExtractor(String key, boolean slashDeposit, String... labels) {
        this.key = key;
        this.slashDeposit = slashDeposit;
        this.labels = List.of(labels);
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public ParseResult<Long> extract(TextDocument doc) {
        final ParseResult<Long> nextLine = extractFrom(doc, true);
        if (nextLine.value() != null) {
            return nextLine;
        }
        // 라벨과 값이 한 줄에 붙어 있는 경우 (설계 I159). 다음 줄을 <b>먼저</b> 보는 이유는
        // 그쪽이 네이버의 일반형이라, 같은 줄을 먼저 보면 엉뚱한 줄을 집을 수 있어서다
        return extractFrom(doc, false);
    }

    private ParseResult<Long> extractFrom(TextDocument doc, boolean nextLine) {
        for (final String label : labels) {
            final Optional<String> value = nextLine
                    ? doc.valueAfter(label)
                    : doc.valueOnSameLine(label);
            if (value.isEmpty()) {
                continue;
            }
            final String raw = value.get();
            if (slashDeposit && raw.contains("/")) {
                final String depositMan = raw.substring(0, raw.indexOf('/')).trim();
                try {
                    return ParseResult.of(Long.parseLong(depositMan.replace(",", "")) * 10_000L, label + ": " + raw);
                } catch (NumberFormatException e) {
                    return ParseResult.missing();
                }
            }
            // 같은 줄에 붙어 온 값은 뒤에 군더더기가 따라온다 (설계 I283)
            final Long won = nextLine ? WonConverter.toWon(raw) : WonConverter.leadingWon(raw);
            if (won != null) {
                return ParseResult.of(won, label + ": " + raw);
            }
        }
        return ParseResult.missing();
    }
}
