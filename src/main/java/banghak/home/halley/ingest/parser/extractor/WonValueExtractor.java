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
        for (final String label : labels) {
            final Optional<String> value = doc.valueAfter(label);
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
            final Long won = WonConverter.toWon(raw);
            if (won != null) {
                return ParseResult.of(won, label + ": " + raw);
            }
        }
        return ParseResult.missing();
    }
}
