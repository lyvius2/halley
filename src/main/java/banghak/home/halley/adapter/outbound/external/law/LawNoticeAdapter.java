package banghak.home.halley.adapter.outbound.external.law;

import banghak.home.halley.application.port.out.external.LawNoticePort;
import banghak.home.halley.domain.loan.RegulationZone;
import banghak.home.halley.domain.regulation.RegulationNotice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 법제처 국가법령정보 어댑터 (설계 I73).
 *
 * <p>세 번 부릅니다 — 목록에서 일련번호를 얻고, 본문에서 발령일자와 첨부 링크를 얻고, 첨부 PDF에서
 * 현황표를 읽습니다. 지정 지역이 본문이 아니라 <b>첨부에</b> 있어서 이 단계가 필요합니다.
 */
@Slf4j
@Component
public class LawNoticeAdapter implements LawNoticePort {

    private static final String TARGET = "admrul";
    private static final String TYPE = "JSON";
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 첨부 링크에서 파일 일련번호만 뽑는다. 링크를 통째로 쓰면 호스트가 바뀔 때 깨진다. */
    private static final Pattern FL_SEQ = Pattern.compile("flSeq=(\\d+)");

    private final LawNoticeFeignClient client;
    private final RegulationNoticePdfParser pdfParser;
    private final ObjectMapper objectMapper;
    /** 법제처가 요구하는 이메일 ID. 인증키가 아니다. */
    private final String oc;

    public LawNoticeAdapter(LawNoticeFeignClient client,
                            RegulationNoticePdfParser pdfParser,
                            ObjectMapper objectMapper,
                            @Value("${law.oc:}") String oc) {
        this.client = client;
        this.pdfParser = pdfParser;
        this.objectMapper = objectMapper;
        this.oc = oc;
    }

    @Override
    public boolean isEnabled() {
        return oc != null && !oc.isBlank();
    }

    @Override
    public Optional<RegulationNotice> fetchLatest(RegulationZone zone) {
        if (!isEnabled()) {
            // 키가 없어 안 부른 것과 불렀는데 실패한 것은 다른 상황이다. 구분되지 않으면 원인을 못 찾는다
            log.info("Skipping law notice lookup - law.oc not configured. zone={}", zone);
            return Optional.empty();
        }
        final String query = zone.label();
        final Optional<String> id = findNoticeId(query);
        if (id.isEmpty()) {
            log.warn("No current law notice found. zone={}, query={}", zone, query);
            return Optional.empty();
        }
        return readNotice(zone, id.get());
    }

    private Optional<String> findNoticeId(String query) {
        final String body = client.search(oc, TARGET, TYPE, query);
        if (body == null) {
            return Optional.empty();
        }
        try {
            final JsonNode root = objectMapper.readTree(body).path("AdmRulSearch").path("admrul");
            // 결과가 1건이면 배열이 아니라 객체로 온다
            final JsonNode first = root.isArray() ? (root.isEmpty() ? null : root.get(0)) : root;
            if (first == null || first.isMissingNode()) {
                return Optional.empty();
            }
            return Optional.ofNullable(first.path("행정규칙일련번호").asString(null));
        } catch (RuntimeException e) {
            log.warn("Failed to parse law notice search. query={}, cause={}", query, e.toString());
            return Optional.empty();
        }
    }

    private Optional<RegulationNotice> readNotice(RegulationZone zone, String id) {
        final String body = client.detail(oc, TARGET, TYPE, id);
        if (body == null) {
            return Optional.empty();
        }
        try {
            final JsonNode service = objectMapper.readTree(body).path("AdmRulService");
            final JsonNode basic = service.path("행정규칙기본정보");
            final String noticeNo = basic.path("발령번호").asString(null);
            final LocalDate announcedOn = parseDate(basic.path("발령일자").asString(null));
            final List<String> areaNames = readAttachment(service, zone);
            if (areaNames.isEmpty()) {
                log.warn("Law notice has no area list - refusing to use it. zone={}, noticeNo={}",
                        zone, noticeNo);
                return Optional.empty();
            }
            log.info("Law notice fetched. zone={}, noticeNo={}, announcedOn={}, areas={}",
                    zone, noticeNo, announcedOn, areaNames.size());
            return Optional.of(new RegulationNotice(zone, noticeNo, announcedOn, areaNames));
        } catch (RuntimeException e) {
            log.warn("Failed to parse law notice detail. zone={}, id={}, cause={}", zone, id, e.toString());
            return Optional.empty();
        }
    }

    private List<String> readAttachment(JsonNode service, RegulationZone zone) {
        final JsonNode attachment = service.path("첨부파일");
        final JsonNode first = attachment.isArray()
                ? (attachment.isEmpty() ? null : attachment.get(0)) : attachment;
        if (first == null || first.isMissingNode()) {
            log.warn("Law notice has no attachment. zone={}", zone);
            return List.of();
        }
        final String link = first.path("첨부파일링크").asString("");
        final Matcher matcher = FL_SEQ.matcher(link);
        if (!matcher.find()) {
            log.warn("Cannot read attachment sequence from link. zone={}, link={}", zone, link);
            return List.of();
        }
        return pdfParser.parseAreaNames(client.download(matcher.group(1)));
    }

    private LocalDate parseDate(String yyyymmdd) {
        try {
            return yyyymmdd == null ? null : LocalDate.parse(yyyymmdd.trim(), YYYYMMDD);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
