package banghak.home.halley.adapter.outbound.external.ministry;

import banghak.home.halley.application.port.out.external.MinistryReferencePort;
import banghak.home.halley.domain.property.ReferenceTrade;
import lombok.extern.slf4j.Slf4j;
import banghak.home.halley.config.RateGate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class MinistryReferenceAdapter implements MinistryReferencePort {

 /** <resultCode>00</resultCode>. 공백·CDATA 가 섞여 와도 읽는다 */
    private static final Pattern RESULT_CODE =
            Pattern.compile("<resultCode>\\s*(?:<!\\[CDATA\\[)?\\s*([^<\\]\\s]+)");
    private static final Pattern RESULT_MSG =
            Pattern.compile("<resultMsg>\\s*(?:<!\\[CDATA\\[)?\\s*([^<\\]]+)");
 /** 정상으로 볼 코드. */
    private static final Pattern OK_CODE = Pattern.compile("^(?:INFO-)?0+$");

    private final MinistryReferenceFeignClient client;
    private final RateGate rateGate;
    private final String serviceKey;

    public MinistryReferenceAdapter(MinistryReferenceFeignClient client,
                                 @Qualifier("ministryRateGate") RateGate rateGate,
                                 @Value("${ministry.service-key:}") String serviceKey) {
        this.client = client;
        this.rateGate = rateGate;
        this.serviceKey = decodeIfEncoded(serviceKey);
    }

 /** 공공데이터포털은 인증키를 Encoding/Decoding 두 형태로 발급한다. Encoding 키(%2F·%3D 포함)를 그대로 넘기면 */
    static String decodeIfEncoded(String key) {
        if (key == null || !key.contains("%")) {
            return key;
        }
        return URLDecoder.decode(key.replace("+", "%2B"), StandardCharsets.UTF_8);
    }


 /** 한 번에 받아 올 건수. */
    private static final int PAGE_SIZE = 100_000;

    @Override
    public List<ReferenceTrade> fetchTrades(String lawdCd, String dealYmd) {
        if (serviceKey == null || serviceKey.isBlank()) {
            return null;
        }
        rateGate.acquire();
        final String xml = client.fetchTrade(serviceKey, lawdCd, dealYmd, PAGE_SIZE);
        if (xml == null || rejected(xml, "trades", lawdCd, dealYmd)) {
            return null;
        }
        final List<ReferenceTrade> trades = parse(xml);
        warnIfTruncated(xml, trades.size(), "trades", lawdCd, dealYmd);
        return trades;
    }

 /** 순수 전세만. */
    @Override
    public List<ReferenceTrade> fetchJeonseDeposits(String lawdCd, String dealYmd) {
        if (serviceKey == null || serviceKey.isBlank()) {
            return null;
        }
        rateGate.acquire();
        final String xml = client.fetchRent(serviceKey, lawdCd, dealYmd, PAGE_SIZE);
        if (xml == null || rejected(xml, "rents", lawdCd, dealYmd)) {
            return null;
        }
        final List<ReferenceTrade> rents = parseRents(xml);
        warnIfTruncated(xml, items(xml).size(), "rents", lawdCd, dealYmd);
        return rents;
    }

 /** 한 페이지에 다 못 담았는지. */
 /** 국토부는 오류도 200으로 줍니다. */
    private boolean rejected(String xml, String what, String lawdCd, String dealYmd) {
        final Matcher matcher = RESULT_CODE.matcher(xml);
        if (!matcher.find()) {
            return false;
        }
        final String code = matcher.group(1).trim();
        if (OK_CODE.matcher(code).matches()) {
            return false;
        }
        if (TOTAL_COUNT.matcher(xml).find()) {
            log.info("Ministry {} had an unknown result code but a real body - storing it. "
                            + "lawdCd={}, dealYmd={}, resultCode={}", what, lawdCd, dealYmd, code);
            return false;
        }
        final Matcher message = RESULT_MSG.matcher(xml);
        log.warn("Ministry {} rejected - not storing this month. lawdCd={}, dealYmd={}, "
                        + "resultCode={}, resultMsg={}",
                what, lawdCd, dealYmd, code, message.find() ? message.group(1).trim() : null);
        return true;
    }

    private void warnIfTruncated(String xml, int received, String what, String lawdCd, String dealYmd) {
        final Matcher matcher = TOTAL_COUNT.matcher(xml);
        if (!matcher.find()) {
            return;
        }
        final int total = Integer.parseInt(matcher.group(1));
        if (total > received) {
            log.warn("Ministry {} were truncated - raise PAGE_SIZE. lawdCd={}, dealYmd={}, "
                            + "totalCount={}, received={}, pageSize={}",
                    what, lawdCd, dealYmd, total, received, PAGE_SIZE);
        }
    }

 /** 응답 어디에나 한 번 나온다. XML 을 다시 파싱하지 않고 뽑는다. */
    private static final Pattern TOTAL_COUNT = Pattern.compile("<totalCount>(\\d+)</totalCount>");

    List<ReferenceTrade> parseRents(String xml) {
        final List<ReferenceTrade> rents = new ArrayList<>();
        for (final Element item : items(xml)) {
            final String monthly = text(item, "monthlyRent", "월세금액");
            if (monthly != null && parseMan(monthly) != 0L) {
                continue;
            }
            final String deposit = text(item, "deposit", "보증금액");
            if (deposit == null) {
                continue;
            }
            rents.add(toRecord(item, parseMan(deposit)));
        }
        return rents;
    }

    List<ReferenceTrade> parse(String xml) {
        try {
            final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new StringReader(xml)));
            final List<ReferenceTrade> trades = new ArrayList<>();
            final NodeList items = document.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                final Element item = (Element) items.item(i);
                trades.add(toTrade(item));
            }
            return trades;
        } catch (Exception e) {
            log.warn("Failed to parse ministry transaction XML. cause={}", e.getMessage());
            return List.of();
        }
    }

    private ReferenceTrade toTrade(Element item) {
        final String priceMan = text(item, "dealAmount", "거래금액");
        return toRecord(item, priceMan == null ? null : parseMan(priceMan));
    }

 /** 매매·전세가 같은 모양이라 금액만 갈아 끼운다. 나머지 칸은 태그가 같다. */
    private ReferenceTrade toRecord(Element item, Long amountWon) {
        final String area = text(item, "excluUseAr", "전용면적");
        final String floor = text(item, "floor", "층");
        final String year = text(item, "dealYear", "년");
        final String month = text(item, "dealMonth", "월");
        final String day = text(item, "dealDay", "일");
        return new ReferenceTrade(
                text(item, "aptNm", "아파트"),
                amountWon,
                area == null ? null : new BigDecimal(area),
                floor == null ? null : Integer.parseInt(floor),
                year == null ? null : LocalDate.of(
                        Integer.parseInt(year),
                        Integer.parseInt(Objects.requireNonNull(month)),
                        Integer.parseInt(Objects.requireNonNull(day))
                ),
                text(item, "umdNm", "법정동"),
                jibunOf(item)
        );
    }

 /** 번지. */
    private String jibunOf(Element item) {
        final String jibun = text(item, "jibun", "지번");
        if (jibun != null && !jibun.isBlank()) {
            return jibun.trim();
        }
        final String bonbun = text(item, "bonbun", "본번");
        if (bonbun == null || bonbun.isBlank()) {
            return null;
        }
        final int main = Integer.parseInt(bonbun.trim());
        final String bubun = text(item, "bubun", "부번");
        final int sub = bubun == null || bubun.isBlank() ? 0 : Integer.parseInt(bubun.trim());
        return sub == 0 ? String.valueOf(main) : main + "-" + sub;
    }

 /** 국토부는 금액을 만원 단위 문자열로 준다 ("110,000"). 원으로 바꾼다. */
    private long parseMan(String value) {
        return Math.round(Double.parseDouble(value.replace(",", "").trim()) * 10_000L);
    }

 /** XML에서 item 요소를 뽑는다. 매매·전세가 같은 구조라 함께 쓴다. */
    private List<Element> items(String xml) {
        try {
            final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new StringReader(xml)));
            final NodeList nodes = document.getElementsByTagName("item");
            final List<Element> elements = new ArrayList<>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                elements.add((Element) nodes.item(i));
            }
            return elements;
        } catch (Exception e) {
            log.warn("Failed to parse ministry XML. cause={}", e.getMessage());
            return List.of();
        }
    }

 /** apis.data.go.kr(현행)은 영문 태그(aptNm·dealAmount), 구 molit 엔드포인트는 국문 태그(아파트·거래금액)를 */
    private String text(Element item, String... tags) {
        for (final String tag : tags) {
            final NodeList nodes = item.getElementsByTagName(tag);
            if (nodes.getLength() == 0) {
                continue;
            }
            final String value = nodes.item(0).getTextContent();
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
