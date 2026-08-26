package banghak.home.halley.ingest.parser;

import java.util.Map;

public record ParsedListing(Map<String, ParseResult<?>> fields) {

    public ParseResult<?> field(String key) {
        return fields.getOrDefault(key, ParseResult.missing());
    }
}
