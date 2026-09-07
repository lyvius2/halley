package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

/** 매물 목록 한 쪽. */
public record ScoredPropertyPage(
        List<ScoredPropertyResponse> items,
        int page,
        int size,
        int total,
        boolean hasNext,
        int archivedTotal
) {
}
