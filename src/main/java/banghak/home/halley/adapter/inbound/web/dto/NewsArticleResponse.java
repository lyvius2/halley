package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.news.NewsArticle;

import java.time.LocalDate;

/** 관련 기사 한 줄. */
public record NewsArticleResponse(String title, String link, String source, LocalDate publishedOn) {

    public static NewsArticleResponse from(NewsArticle article) {
        return new NewsArticleResponse(article.title(), article.link(),
                article.source(), article.publishedOn());
    }
}
