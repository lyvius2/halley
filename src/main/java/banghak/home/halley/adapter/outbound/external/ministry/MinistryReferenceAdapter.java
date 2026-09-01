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

    /**
     * 공공데이터포털은 인증키를 Encoding/Decoding 두 형태로 발급한다. Encoding 키(`%2F`·`%3D` 포함)를 그대로 넘기면
     * Feign이 `%`를 한 번 더 인코딩해 403 SERVICE_KEY_IS_NOT_REGISTERED_ERROR가 난다. 어느 형태를 넣어도 동작하도록
     * 퍼센트 이스케이프만 되돌린다(`+`는 Base64 키의 문자이므로 공백으로 바뀌지 않게 보호).
     */
    static String decodeIfEncoded(String key) {
        if (key == null || !key.contains("%")) {
            return key;
        }
        return URLDecoder.decode(key.replace("+", "%2B"), StandardCharsets.UTF_8);
    }


    /**
     * 한 번에 받아 올 건수 (설계 I219).
     *
     * <p><b>안 주면 10건입니다.</b> 서울 한 구의 한 달 아파트 매매는 실측으로
     * 200~700건이라, 10건만 보면 <b>찾는 단지가 거의 안 걸립니다.</b>
     *
     * <p>1000으로 뒀다가 <b>더 재 보고 올렸습니다.</b> 송파구 2025-03 이 952건이라
     * 여유가 48건뿐이었습니다 — 거래가 몰리는 달에 조용히 잘릴 자리였습니다.
     *
     * <pre>
     * 송파(11710) 2025-03  952      강남(11680) 2025-03  917
     * 노원(11350) 2025-06  885      성북(11290) 2025-06  660
     * </pre>
     *
     * <p>그래도 넘칠 수 있으므로 <b>넘쳤는지 확인합니다</b>(`warnIfTruncated`).
     * 주석에 "어긋나면 알 수 있다"고 적어 두고 <b>비교하는 코드는 없었습니다.</b>
     */
    private static final int PAGE_SIZE = 3000;

    @Override
    public List<ReferenceTrade> fetchTrades(String lawdCd, String dealYmd) {
        // 키가 없는 것도 '모르는 것'이다 — 0건으로 굳히면 안 된다 (설계 I140)
        if (serviceKey == null || serviceKey.isBlank()) {
            return null;
        }
        rateGate.acquire();
        final String xml = client.fetchTrade(serviceKey, lawdCd, dealYmd, PAGE_SIZE);
        if (xml == null) {
            return null;
        }
        final List<ReferenceTrade> trades = parse(xml);
        warnIfTruncated(xml, trades.size(), "trades", lawdCd, dealYmd);
        return trades;
    }

    /**
     * 순수 전세만 (설계 I131).
     *
     * <p>돌려주는 {@code dealAmount}는 <b>보증금</b>입니다 — 매매가가 아닙니다.
     * 월세가 붙은 반전세는 보증금이 낮게 잡혀 전세가율을 왜곡하므로 <b>여기서 걸러 냅니다.</b>
     */
    @Override
    public List<ReferenceTrade> fetchJeonseDeposits(String lawdCd, String dealYmd) {
        if (serviceKey == null || serviceKey.isBlank()) {
            return null;
        }
        rateGate.acquire();
        final String xml = client.fetchRent(serviceKey, lawdCd, dealYmd, PAGE_SIZE);
        if (xml == null) {
            return null;
        }
        final List<ReferenceTrade> rents = parseRents(xml);
        // 전세는 월세를 걸러 내므로 받은 수보다 적다 — 자른 것과 구분되지 않아 총량으로 본다
        warnIfTruncated(xml, items(xml).size(), "rents", lawdCd, dealYmd);
        return rents;
    }

    /**
     * 한 페이지에 다 못 담았는지 (설계 I229).
     *
     * <p>국토부는 <b>넘쳐도 아무 말 없이</b> 앞에서 잘라 줍니다. 그러면 뒤쪽 거래는
     * 통째로 안 보이는데, 화면에는 "거래가 없다"로 나타납니다 —
     * <b>없는 것과 못 받은 것을 구분할 수 없습니다.</b>
     *
     * <p>이걸 안 봐서 <b>10건만 받던 것을 반년 넘게 몰랐습니다</b>([I219]).
     */
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

    /** 응답 어디에나 한 번 나온다 — XML 을 다시 파싱하지 않고 뽑는다. */
    private static final Pattern TOTAL_COUNT = Pattern.compile("<totalCount>(\\d+)</totalCount>");

    List<ReferenceTrade> parseRents(String xml) {
        final List<ReferenceTrade> rents = new ArrayList<>();
        for (final Element item : items(xml)) {
            // 월세가 0이 아니면 반전세다. 보증금이 낮아 전세가율을 왜곡한다
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

    /** 매매·전세가 같은 모양이라 금액만 갈아 끼운다 — 나머지 칸은 태그가 같다. */
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
                )
        );
    }

    /** 국토부는 금액을 <b>만원 단위 문자열</b>로 준다 (`"110,000"`). 원으로 바꾼다. */
    private long parseMan(String value) {
        return Math.round(Double.parseDouble(value.replace(",", "").trim()) * 10_000L);
    }

    /** XML에서 {@code item} 요소를 뽑는다. 매매·전세가 같은 구조라 함께 쓴다. */
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

    /**
     * apis.data.go.kr(현행)은 영문 태그(`aptNm`·`dealAmount`), 구 molit 엔드포인트는 국문 태그(`아파트`·`거래금액`)를
     * 사용한다. 후보 태그를 순서대로 찾아 먼저 값이 있는 쪽을 쓴다.
     */
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
