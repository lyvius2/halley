package banghak.home.halley.ingest.parser;

import banghak.home.halley.ingest.parser.extractor.ApprovalYearExtractor;
import banghak.home.halley.ingest.parser.extractor.AreaValueExtractor;
import banghak.home.halley.ingest.parser.extractor.DealTypeExtractor;
import banghak.home.halley.ingest.parser.extractor.FloorExtractor;
import banghak.home.halley.ingest.parser.extractor.IntegerValueExtractor;
import banghak.home.halley.ingest.parser.extractor.LabelValueExtractor;
import banghak.home.halley.ingest.parser.extractor.MoveInExtractor;
import banghak.home.halley.ingest.parser.extractor.ParkingExtractor;
import banghak.home.halley.ingest.parser.extractor.WonValueExtractor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NaverListingTextParser {

    private final List<FieldExtractor<?>> extractors;

    public NaverListingTextParser(List<FieldExtractor<?>> extractors) {
        this.extractors = extractors;
    }

    public static List<FieldExtractor<?>> defaultExtractors() {
        return List.of(
                new LabelValueExtractor("name", "단지명"),
                new LabelValueExtractor("naverArticleNo", "매물번호"),
                new DealTypeExtractor(),
                new WonValueExtractor("priceDeposit", true, "매매가", "보증금"),
                new WonValueExtractor("priceMonthly", false, "월세"),
                new WonValueExtractor("kbPrice", false, "KB시세"),
                new AreaValueExtractor("areaSupplyM2", "공급면적"),
                new AreaValueExtractor("areaExclusiveM2", "전용면적"),
                new FloorExtractor(),
                new LabelValueExtractor("direction", "향"),
                new LabelValueExtractor("addressJibun", "지번주소"),
                new ApprovalYearExtractor("approvalYear", "사용승인일"),
                new IntegerValueExtractor("totalHouseholds", "세대수"),
                new ParkingExtractor(),
                new MoveInExtractor());
    }

    public List<FieldExtractor<?>> extractors() {
        return extractors;
    }

    public ParsedListing parse(String rawText) {
        final TextDocument doc = new TextDocument(rawText == null ? "" : rawText);
        final Map<String, ParseResult<?>> fields = new LinkedHashMap<>();
        for (final FieldExtractor<?> extractor : extractors) {
            fields.put(extractor.key(), extractor.extract(doc));
        }
        return new ParsedListing(fields);
    }
}
