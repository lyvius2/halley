package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.news.NewsArticle;

import java.util.List;

/**
 * 관련 기사 검색 (설계 I137).
 *
 * <p><b>결과를 점수나 프롬프트에 넣지 마십시오.</b> 화면에 링크 목록으로만 씁니다 —
 * 부동산 기사는 이해관계자가 만드는 것이 많고, LLM에 먹이면 프롬프트 주입 통로가 됩니다.
 */
public interface NewsSearchPort {

    boolean isEnabled();

    /** 최신순. 못 받으면 빈 목록 — 기사가 없다고 화면이 멈추지 않습니다. */
    List<NewsArticle> search(String query, int limit);
}
