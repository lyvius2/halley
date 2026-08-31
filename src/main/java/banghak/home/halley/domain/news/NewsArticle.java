package banghak.home.halley.domain.news;

import java.time.LocalDate;

/**
 * 관련 기사 하나 (설계 I137).
 *
 * <p><b>점수에 반영하지 않습니다.</b> 제목·날짜·출처·링크만 보여 주고 판단은 사람이 합니다.
 * <b>LLM 프롬프트에도 넣지 않습니다</b> — 웹 내용은 신뢰할 수 없는 입력이라
 * 프롬프트 주입 통로가 됩니다(설계 4-B).
 *
 * @param source 원 언론사 도메인. 네이버 뉴스 링크가 아니라 <b>originallink</b>에서 뽑는다 —
 *               "어디가 쓴 기사인가"가 판단의 재료다
 */
public record NewsArticle(
        String title,
        String link,
        String source,
        LocalDate publishedOn
) {
}
