package banghak.home.halley.adapter.outbound.external.ministry;

import banghak.home.halley.application.port.out.external.MinistryReferencePort;
import banghak.home.halley.domain.property.ReferenceTrade;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class MinistryReferenceAdapter implements MinistryReferencePort {

    private final MinistryReferenceFeignClient client;
    private final String serviceKey;

    public MinistryReferenceAdapter(MinistryReferenceFeignClient client,
                                 @Value("${ministry.service-key:}") String serviceKey) {
        this.client = client;
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

    @Override
    public List<ReferenceTrade> fetchTrades(String lawdCd, String dealYmd) {
        if (serviceKey == null || serviceKey.isBlank()) {
            return List.of();
        }
        final String xml = client.fetchTrade(serviceKey, lawdCd, dealYmd);
        if (xml == null) {
            return List.of();
        }
        return parse(xml);
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
        final String area = text(item, "excluUseAr", "전용면적");
        final String floor = text(item, "floor", "층");
        final String year = text(item, "dealYear", "년");
        final String month = text(item, "dealMonth", "월");
        final String day = text(item, "dealDay", "일");
        return new ReferenceTrade(
                text(item, "aptNm", "아파트"),
                priceMan == null ? null : Math.round(Double.parseDouble(priceMan.replace(",", "")) * 10_000L),
                area == null ? null : new BigDecimal(area),
                floor == null ? null : Integer.parseInt(floor),
                year == null ? null : LocalDate.of(
                        Integer.parseInt(year),
                        Integer.parseInt(Objects.requireNonNull(month)),
                        Integer.parseInt(Objects.requireNonNull(day))
                )
        );
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
