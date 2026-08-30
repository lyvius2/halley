package banghak.home.halley.ingest.parser.extractor;

import banghak.home.halley.ingest.parser.FieldExtractor;
import banghak.home.halley.ingest.parser.ParseResult;
import banghak.home.halley.ingest.parser.TextDocument;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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
    /**
     * <b>사무소명을 기준점으로 삼는다.</b> 줄 순서만 믿으면 안 된다 — 실측에서 어떤 매물은
     * `중개소` 아래에 `중개사 프로필 이미지`라는 이미지 대체텍스트가 한 줄 더 들어와,
     * 이름 자리에 그 문구가, 사무소명 자리에 <b>사람 이름</b>이 저장됐다 (설계 I82).
     */
    private static final Pattern OFFICE_NAME = Pattern.compile(
            ".*(공인중개사사무소|중개사사무소|부동산중개|공인중개사|중개사무소)$");
    /** 기준점을 못 찾았을 때 걸러낼 UI 문구. */
    private static final Pattern UI_NOISE = Pattern.compile(".*(이미지|프로필|더보기|상세보기)\\s*$");
    private static final int OFFICE_BLOCK_LINES = 4;
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
            case AGENT_NAME -> office(agentSection, false);
            case OFFICE_NAME -> office(agentSection, true);
            case PHONE -> phone(agentSection, 0);
            case MOBILE -> phone(agentSection, 1);
            case ADDRESS -> value(agentSection, "위치");
            case REGISTRATION_NO -> value(agentSection, "등록번호");
        };
    }

    /**
     * @param wantOffice true면 사무소명, false면 중개인 이름
     */
    private ParseResult<String> office(TextDocument section, boolean wantOffice) {
        final List<String> lines = section.linesAfterUntil("중개소", OFFICE_STOP, OFFICE_BLOCK_LINES);
        if (lines.isEmpty()) {
            return ParseResult.missing();
        }
        final String trace = "중개소: " + String.join(" / ", lines);
        final int officeAt = indexOfOffice(lines);
        if (officeAt >= 0) {
            if (wantOffice) {
                return ParseResult.of(lines.get(officeAt), trace);
            }
            // 이름은 사무소명 바로 앞줄이다. 사무소명이 첫 줄이면 이름이 없는 매물이다
            return officeAt == 0 ? ParseResult.missing() : ParseResult.of(lines.get(officeAt - 1), trace);
        }
        // 기준점을 못 찾으면 순서에 기대되, 명백한 UI 문구는 건너뛴다
        final List<String> candidates = lines.stream().filter(l -> !UI_NOISE.matcher(l).matches()).toList();
        final int index = wantOffice ? 1 : 0;
        return candidates.size() > index
                ? ParseResult.of(candidates.get(index), trace)
                : ParseResult.missing();
    }

    private int indexOfOffice(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (OFFICE_NAME.matcher(lines.get(i)).matches()) {
                return i;
            }
        }
        return -1;
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
