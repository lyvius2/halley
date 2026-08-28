package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 중개사 블록에서 항목 하나를 뽑는다 (설계 9.2 · I53).
 *
 * <p>네이버는 `중개사` 섹션 아래에 이름·사무소명·전화·위치·등록번호를 둔다. `위치`는 단지 정보에도
 * 나오는 중복 라벨이라, 반드시 `중개사` 이후 구간에서만 찾는다.
 * 전화는 `02-764-4222010-7407-4222`처럼 유선·휴대폰이 붙어 오므로 정규식 반복 매치로 가른다.
 */
public class AgentExtractor implements FieldExtractor<String> {

    /** 중개소 라벨 아래에 이름·사무소명이 순서대로 온다. */
    private static final Set<String> OFFICE_STOP = Set.of("상세보기", "전화", "위치", "등록번호");
    private static final String PHONE_REGEX = "(0\\d{1,2}-\\d{3,4}-\\d{4})";

    public enum Target { AGENT_NAME, OFFICE_NAME, PHONE, MOBILE, ADDRESS, REGISTRATION_NO }

    private final String key;
    private final Target target;

    public AgentExtractor(String key, Target target) {
        this.key = key;
        this.target = target;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public ParseResult<String> extract(TextDocument doc) {
        final TextDocument agentSection = doc.after("중개사");
        return switch (target) {
            case AGENT_NAME -> office(agentSection, 0);
            case OFFICE_NAME -> office(agentSection, 1);
            case PHONE -> phone(agentSection, 0);
            case MOBILE -> phone(agentSection, 1);
            case ADDRESS -> value(agentSection, "위치");
            case REGISTRATION_NO -> value(agentSection, "등록번호");
        };
    }

    private ParseResult<String> office(TextDocument section, int index) {
        final List<String> lines = section.linesAfterUntil("중개소", OFFICE_STOP, 2);
        if (lines.size() <= index) {
            return ParseResult.missing();
        }
        return ParseResult.of(lines.get(index), "중개소: " + String.join(" / ", lines));
    }

    private ParseResult<String> phone(TextDocument section, int index) {
        final List<String> phones = section.after("전화").allMatches(PHONE_REGEX);
        if (phones.size() <= index) {
            return ParseResult.missing();
        }
        return ParseResult.of(phones.get(index), "전화: " + String.join(" / ", phones));
    }

    private ParseResult<String> value(TextDocument section, String label) {
        final Optional<String> value = section.valueAfter(label);
        return value.map(v -> ParseResult.of(v, label + ": " + v)).orElseGet(ParseResult::missing);
    }
}
