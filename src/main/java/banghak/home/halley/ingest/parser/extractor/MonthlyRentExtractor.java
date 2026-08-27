package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.money.WonConverter;
import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.Optional;

public class MonthlyRentExtractor implements FieldExtractor<Long> {

    @Override
    public String key() {
        return "priceMonthly";
    }

    @Override
    public ParseResult<Long> extract(TextDocument doc) {
        final Optional<String> monthly = doc.valueAfter("월세");
        if (monthly.isPresent()) {
            final Long won = WonConverter.toWon(monthly.get());
            if (won != null) {
                return ParseResult.of(won, "월세: " + monthly.get());
            }
        }
        final Optional<String> deposit = doc.valueAfter("보증금");
        if (deposit.isPresent() && deposit.get().contains("/")) {
            final String right = deposit.get().substring(deposit.get().indexOf('/') + 1).trim();
            try {
                return ParseResult.of(Long.parseLong(right.replace(",", "")) * 10_000L, "보증금: " + deposit.get());
            } catch (NumberFormatException e) {
                return ParseResult.missing();
            }
        }
        return ParseResult.missing();
    }
}
