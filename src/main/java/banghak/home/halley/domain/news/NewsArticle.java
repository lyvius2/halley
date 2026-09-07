package banghak.home.halley.domain.news;

import java.time.LocalDate;

/** 관련 기사 하나. */
public record NewsArticle(
        String title,
        String link,
        String source,
        LocalDate publishedOn
) {
}
