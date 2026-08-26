package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.ingest.parser.Confidence;

public record ParsedFieldResponse(
        String key,
        String value,
        Confidence confidence,
        String rawSnippet,
        String note
) {
}
