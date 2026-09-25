package banghak.home.halley.domain.news;

import java.time.LocalDate;

public record NewsArticle(
        String title,
        String link,
        String source,
        LocalDate publishedOn
) {
}
