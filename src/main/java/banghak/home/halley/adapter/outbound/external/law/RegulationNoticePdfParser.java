package banghak.home.halley.adapter.outbound.external.law;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 규제지역 고시 첨부 PDF에서 <b>지정 현황표</b>를 읽는다 (설계 I73).
 *
 * <p><b>추출된 텍스트가 깨져 있습니다.</b> 한글 문서에서 나온 PDF라 숫자와 문장부호가 줄 끝으로
 * 밀립니다 — {@code 강남구 서초구 송파구, , ,} 처럼 쉼표가 뒤로 몰립니다. 그래서 <b>구분자를 믿지
 * 않고</b> 부호를 모두 지운 뒤 공백으로 토큰화합니다. 지역명 토큰 자체는 순서까지 온전합니다.
 *
 * <p>표는 `시도 / 현행 / 조정` 3열이고 변동이 없는 시도는 `좌동`으로 적힙니다. `현행`과
 * `<신규 지정>`을 합친 것이 그 시점의 전체 현황입니다.
 */
@Slf4j
@Component
public class RegulationNoticePdfParser {

    /** 표가 시작되는 자리. 이 앞은 지정 근거·효력이라 지역명이 없다. */
    private static final Pattern TABLE_START = Pattern.compile("지정\\s*현황");
    /** `('26.7.1.)` 같은 조정일자 표기 — 지우지 않으면 숫자가 지역명으로 섞인다. */
    private static final Pattern ADJUST_DATE = Pattern.compile("\\('?\\d{2}\\.[\\d.]*\\)");
    /** 쉼표·마침표·가운뎃점·꺾쇠·※ 와 조합되지 않은 낱자모(`ᄋ`). */
    private static final Pattern PUNCTUATION = Pattern.compile("[,.·<>※\\u3131-\\u318E\\u1100-\\u11FF]");

    /** 표의 1열에 오는 시도명. 이 토큰을 만나면 그 뒤부터 해당 시도의 지역이다. */
    private static final Set<String> SIDO = Set.of(
            "서울", "경기", "인천", "부산", "대구", "광주", "대전", "울산", "세종",
            "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");
    /** 표 머리글과 `좌동` 같은 표기 — 지역명이 아니다. */
    private static final Set<String> NOISE = Set.of(
            "시도", "현", "행", "조", "정", "좌동", "신규", "지정", "현황", "해제", "구분");

    /**
     * @return `서울 강남구`, `경기 화성동탄` 형식의 지역명 목록. 시도를 붙여 두지 않으면
     *         `중구`처럼 여러 시도에 있는 이름을 구분할 수 없다
     */
    public List<String> parseAreaNames(byte[] pdf) {
        final String text = extractText(pdf);
        if (text == null) {
            return List.of();
        }
        final Matcher start = TABLE_START.matcher(text);
        if (!start.find()) {
            // 표가 없으면 고시 서식이 바뀐 것이다. 조용히 빈 목록을 주면 규제지역이 통째로 사라진다
            log.warn("Regulation notice PDF has no status table - format may have changed. chars={}",
                    text.length());
            return List.of();
        }
        String body = text.substring(start.end());
        body = ADJUST_DATE.matcher(body).replaceAll(" ");
        body = PUNCTUATION.matcher(body).replaceAll(" ");

        final List<String> areas = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        String sido = null;
        for (final String token : body.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if (SIDO.contains(token)) {
                sido = token;
            } else if (NOISE.contains(token) || sido == null) {
                // 시도가 나오기 전의 토큰은 표 머리글이다
                continue;
            } else if (seen.add(sido + " " + token)) {
                areas.add(sido + " " + token);
            }
        }
        return areas;
    }

    private String extractText(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            log.warn("Regulation notice PDF is empty - cannot read the status table.");
            return null;
        }
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        } catch (Exception e) {
            log.warn("Failed to read regulation notice PDF. cause={}", e.toString());
            return null;
        }
    }
}
