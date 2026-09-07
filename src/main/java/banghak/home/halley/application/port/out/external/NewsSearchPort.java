package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.news.NewsArticle;

import java.util.List;

/** 관련 기사 검색. */
public interface NewsSearchPort {

    boolean isEnabled();

 /** 최신순. 못 받으면 빈 목록. 기사가 없다고 화면이 멈추지 않습니다. */
    List<NewsArticle> search(String query, int limit);
}
