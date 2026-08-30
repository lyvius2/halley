package banghak.home.halley.ingest.parser;

import banghak.home.halley.ingest.parser.extractor.AgentExtractor;
import banghak.home.halley.ingest.parser.extractor.ApprovalYearExtractor;
import banghak.home.halley.ingest.parser.extractor.BrokerageExtractor;
import banghak.home.halley.ingest.parser.extractor.AreaValueExtractor;
import banghak.home.halley.ingest.parser.extractor.DealTypeExtractor;
import banghak.home.halley.ingest.parser.extractor.DongHoExtractor;
import banghak.home.halley.ingest.parser.extractor.FloorExtractor;
import banghak.home.halley.ingest.parser.extractor.IntegerValueExtractor;
import banghak.home.halley.ingest.parser.extractor.LabelValueExtractor;
import banghak.home.halley.ingest.parser.extractor.ValueCleaner;
import banghak.home.halley.ingest.parser.extractor.MaintenanceFeeExtractor;
import banghak.home.halley.ingest.parser.extractor.MoveInExtractor;
import banghak.home.halley.ingest.parser.extractor.NameExtractor;
import banghak.home.halley.ingest.parser.extractor.ParkingExtractor;
import banghak.home.halley.ingest.parser.extractor.SchoolExtractor;
import banghak.home.halley.ingest.parser.extractor.TaxExtractor;
import banghak.home.halley.ingest.parser.extractor.WalkMinutesExtractor;
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
                new NameExtractor(),
                new LabelValueExtractor("naverArticleNo", "매물번호"),
                new DongHoExtractor(),
                new DealTypeExtractor(),
                // 전세는 실제 페이지가 `전세가`로 적는다. `보증금`만 보던 탓에 전세 매물의
                // 가격이 통째로 비었다 (설계 I82)
                new WonValueExtractor("priceDeposit", true, "매매가", "전세가", "보증금"),
                new WonValueExtractor("kbPrice", false, "KB시세"),
                new MaintenanceFeeExtractor(),
                new AreaValueExtractor("areaSupplyM2", "공급면적"),
                new AreaValueExtractor("areaExclusiveM2", "전용면적"),
                new FloorExtractor(),
                new LabelValueExtractor("roomBath", ValueCleaner.ROOM_BATH, "방/욕실", "방수/욕실수"),
                new LabelValueExtractor("direction", ValueCleaner.DIRECTION, "향"),
                new LabelValueExtractor("heatingType", "난방"),
                new LabelValueExtractor("addressJibun", "지번주소", "위치"),
                new LabelValueExtractor("subway", "지하철"),
                new WalkMinutesExtractor("subwayMinutes", "지하철"),
                new SchoolExtractor("school", "배정 초등학교", "초등학교"),
                new WalkMinutesExtractor("schoolMinutes", "배정 초등학교", "초등학교"),
                new ApprovalYearExtractor("approvalYear", "사용승인일"),
                new IntegerValueExtractor("totalHouseholds", "세대수"),
                new ParkingExtractor(),
                new MoveInExtractor(),
                // 중개사 (설계 I53)
                new AgentExtractor("agentName", AgentExtractor.Target.AGENT_NAME),
                new AgentExtractor("agentOfficeName", AgentExtractor.Target.OFFICE_NAME),
                new AgentExtractor("agentPhone", AgentExtractor.Target.PHONE),
                new AgentExtractor("agentMobile", AgentExtractor.Target.MOBILE),
                new AgentExtractor("agentAddress", AgentExtractor.Target.ADDRESS),
                new AgentExtractor("agentRegistrationNo", AgentExtractor.Target.REGISTRATION_NO),
                // 중개보수·세금
                new BrokerageExtractor("brokerageFee", BrokerageExtractor.Target.FEE),
                new BrokerageExtractor("brokerageRate", BrokerageExtractor.Target.RATE),
                new TaxExtractor("acquisitionTax", TaxExtractor.Target.ACQUISITION),
                new TaxExtractor("propertyTax", TaxExtractor.Target.PROPERTY),
                new TaxExtractor("comprehensiveTax", TaxExtractor.Target.COMPREHENSIVE));
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
