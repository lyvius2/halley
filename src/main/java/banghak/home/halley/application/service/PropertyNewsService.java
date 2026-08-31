package banghak.home.halley.application.service;

import banghak.home.halley.application.port.out.external.NewsSearchPort;
import banghak.home.halley.domain.news.NewsArticle;
import banghak.home.halley.domain.property.Property;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 매물에 관련된 기사를 찾는다 (설계 I137).
 *
 * <p><b>점수에도 프롬프트에도 넣지 않습니다.</b> 화면에 링크 목록으로만 씁니다.
 *
 * <p>검색어는 <b>단지명 + 지역</b>입니다. 단지명만 넣으면 동명이 단지가 섞이고,
 * 지역만 넣으면 이 매물과 무관한 기사가 옵니다.
 */
@Slf4j
@Service
public class PropertyNewsService {

    /** `서울 강남구 대치동 316` → `강남구 대치동`. */
    private static final Pattern DISTRICT =
            Pattern.compile("([가-힣]+[시군구])\\s+([가-힣]+[동읍면])");
    /** 이보다 짧은 단지명은 검색어로 쓸 수 없다 — 아무 기사나 걸린다. */
    private static final int MIN_NAME_LENGTH = 2;

    private final NewsSearchPort newsSearchPort;
    private final PropertyAccessGuard propertyAccessGuard;
    private final int limit;

    public PropertyNewsService(NewsSearchPort newsSearchPort,
                               PropertyAccessGuard propertyAccessGuard,
                               @Value("${naver.news-limit:10}") int limit) {
        this.newsSearchPort = newsSearchPort;
        this.propertyAccessGuard = propertyAccessGuard;
        this.limit = limit;
    }

    public List<NewsArticle> find(Long propertyId) {
        final Property property = propertyAccessGuard.require(propertyId);
        if (!newsSearchPort.isEnabled()) {
            return List.of();
        }
        final String query = queryOf(property);
        if (query == null) {
            log.info("Skipping news search - no usable query. propertyId={}", propertyId);
            return List.of();
        }
        return newsSearchPort.search(query, limit);
    }

    /**
     * 단지명 + 지역.
     *
     * <p>단지명이 없거나 너무 짧으면 <b>검색하지 않습니다</b> — 아무 기사나 걸리면
     * 사용자가 무관한 것을 이 매물의 정보로 읽습니다.
     */
    String queryOf(Property property) {
        final String name = property.name();
        if (name == null || name.replaceAll("\\s", "").length() < MIN_NAME_LENGTH) {
            return null;
        }
        final String district = districtOf(property);
        return district == null ? name.trim() : district + " " + name.trim();
    }

    private String districtOf(Property property) {
        final String address = property.addressJibun() != null && !property.addressJibun().isBlank()
                ? property.addressJibun() : property.addressRoad();
        if (address == null) {
            return null;
        }
        final var matcher = DISTRICT.matcher(address);
        return matcher.find() ? matcher.group(1) + " " + matcher.group(2) : null;
    }
}
