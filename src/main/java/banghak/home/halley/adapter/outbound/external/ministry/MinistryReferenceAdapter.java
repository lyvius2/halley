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
        this.serviceKey = serviceKey;
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
            log.warn("국토부 실거래가 XML 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private ReferenceTrade toTrade(Element item) {
        final String priceMan = text(item, "거래금액");
        final String area = text(item, "전용면적");
        final String floor = text(item, "층");
        final String year = text(item, "년");
        final String month = text(item, "월");
        final String day = text(item, "일");
        return new ReferenceTrade(
                text(item, "아파트"),
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

    private String text(Element item, String tag) {
        final NodeList nodes = item.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        final String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
