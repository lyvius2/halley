package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.news.NewsArticle;

import java.time.LocalDate;

/**
 * 관련 기사 한 줄 (설계 I137).
 *
 * <p>화면에 <b>"검증하지 않은 외부 기사"</b>라고 밝힙니다 — 부동산 기사는 이해관계자가
 * 만드는 것이 많고, 우리는 내용을 판단하지 않습니다.
 */
public record NewsArticleResponse(String title, String link, String source, LocalDate publishedOn) {

    public static NewsArticleResponse from(NewsArticle article) {
        return new NewsArticleResponse(article.title(), article.link(),
                article.source(), article.publishedOn());
    }
}
