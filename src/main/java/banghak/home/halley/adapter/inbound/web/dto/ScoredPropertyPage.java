package banghak.home.halley.adapter.inbound.web.dto;

import java.util.List;

/**
 * 매물 목록 한 쪽.
 *
 * @param items   이 쪽에 실린 매물
 * @param page    0부터 세는 쪽 번호
 * @param size    한 쪽의 크기
 * @param total   거른 뒤의 전체 건수 — 화면의 뱃지가 이 값을 씁니다
 * @param hasNext 더 있는가. page*size + items.size() < total 을 화면이 다시
 *                계산하게 두면 세는 규칙이 두 벌이 됩니다
 * @param archivedTotal 치워 둔 매물이 몇 건인가. 아카이빙 탭의 뱃지가 씁니다 —
 *                      지금 보고 있는 탭과 무관하게 늘 실립니다. 치워 둔 것이
 *                      몇 건인지 모르면 치웠다는 사실 자체를 잊습니다
 */
public record ScoredPropertyPage(
        List<ScoredPropertyResponse> items,
        int page,
        int size,
        int total,
        boolean hasNext,
        int archivedTotal
) {
}
