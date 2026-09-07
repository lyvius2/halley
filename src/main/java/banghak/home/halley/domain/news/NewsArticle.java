package banghak.home.halley.domain.news;

import java.time.LocalDate;

/**
 * 관련 기사 하나.
 *
 * 점수에 반영하지 않습니다. 제목·날짜·출처·링크만 보여 주고 판단은 사람이 합니다.
 * LLM 프롬프트에도 넣지 않습니다 — 웹 내용은 신뢰할 수 없는 입력이라
 * 프롬프트 주입 통로가 됩니다.
 *
 * @param source 원 언론사 도메인. 네이버 뉴스 링크가 아니라 originallink에서 뽑는다 —
 *               "어디가 쓴 기사인가"가 판단의 재료다
 */
public record NewsArticle(
        String title,
        String link,
        String source,
        LocalDate publishedOn
) {
}
