package banghak.home.halley.adapter.inbound.web.dto;

/**
 * 매물 하나의 채점 판 번호 (설계 I85).
 *
 * <p>채점은 <b>사용자가 보고 있는 동안 뒤에서 바뀝니다</b> — 보정이 끝나고, AI 응답이 옵니다.
 * 화면이 그걸 알아채려면 목록을 통째로 다시 받아 비교해야 하는데, 매번 그러기에는 무겁습니다.
 * 이 번호만 확인하고 <b>달라졌을 때만</b> 목록을 받습니다.
 */
public record ScoreVersionResponse(Long propertyId, long scoreVersion) {
}
