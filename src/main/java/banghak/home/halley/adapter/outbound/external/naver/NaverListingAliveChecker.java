package banghak.home.halley.adapter.outbound.external.naver;

import banghak.home.halley.config.exception.ListingCheckFailedException;
import banghak.home.halley.domain.property.ListingAliveChecker;
import banghak.home.halley.domain.property.ListingCheckResult;
import banghak.home.halley.domain.property.ListingVerdict;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NaverListingAliveChecker implements ListingAliveChecker {

    private static final Pattern ARTICLE_NO = Pattern.compile("/articles/(\\d+)");
    private static final String GONE_MARKER = "매물이 없습니다";

    private final NaverArticleFeignClient client;

    public NaverListingAliveChecker(NaverArticleFeignClient client) {
        this.client = client;
    }

    @Override
    public ListingCheckResult check(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return error("source_url 없음");
        }
        final Matcher matcher = ARTICLE_NO.matcher(sourceUrl);
        if (!matcher.find()) {
            return error("articleNo 추출 실패: " + sourceUrl);
        }
        final String articleNo = matcher.group(1);
        try {
            final String body = client.fetch(articleNo);
            if (body == null) {
                return error("응답 없음");
            }
            if (body.contains(GONE_MARKER)) {
                return ListingCheckResult.of(ListingVerdict.GONE, "삭제 마커 발견", 200);
            }
            return ListingCheckResult.of(ListingVerdict.ALIVE, "매물 데이터 존재", 200);
        } catch (ListingCheckFailedException e) {
            return classify(e);
        }
    }

    private ListingCheckResult classify(ListingCheckFailedException e) {
        final Integer status = e.getHttpStatus();
        if (status != null && (status == 403 || status == 429)) {
            return ListingCheckResult.of(ListingVerdict.BLOCKED, "봇 차단 HTTP " + status, status);
        }
        return error("HTTP/네트워크 오류 (" + (status == null ? "timeout" : status) + ")");
    }

    private ListingCheckResult error(String evidence) {
        return ListingCheckResult.of(ListingVerdict.ERROR, evidence, null);
    }
}
