package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.ParsePreviewResponse;
import banghak.home.halley.adapter.inbound.web.dto.ParsedFieldResponse;
import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.NaverListingTextParser;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.ParsedListing;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ParsePreviewService {

    private final NaverListingTextParser parser;

    public ParsePreviewService(NaverListingTextParser parser) {
        this.parser = parser;
    }

    public ParsePreviewResponse preview(String text) {
        final ParsedListing parsed = parser.parse(text);
        final boolean isListing = parsed.field("naverArticleNo").isPresent();
        final List<ParsedFieldResponse> fields = parser.extractors().stream()
                .map(FieldExtractor::key)
                .map(key -> toResponse(key, parsed.field(key)))
                .toList();
        return new ParsePreviewResponse(isListing, fields);
    }

    private ParsedFieldResponse toResponse(String key, ParseResult<?> result) {
        return new ParsedFieldResponse(
                key,
                result.value() == null ? null : String.valueOf(result.value()),
                result.confidence(),
                result.rawSnippet(),
                result.note());
    }
}
