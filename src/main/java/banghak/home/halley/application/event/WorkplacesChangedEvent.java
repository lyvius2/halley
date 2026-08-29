package banghak.home.halley.application.event;

/**
 * 사용자가 추가되거나 직장 위치·활성 상태가 바뀌었다 (설계 I60).
 *
 * <p>AI 추천도의 입력에 구매자들의 직장 위치가 들어가므로, 이 값이 바뀌면 추천도를 다시 뽑아야 합니다.
 * LLM 호출은 느려서 트랜잭션 안에서 돌릴 수 없으므로 커밋 뒤 비동기로 처리합니다.
 *
 * @param cause 로그에 남길 계기 — 어떤 조작이 재추론을 부른 건지 추적할 수 있어야 한다
 */
public record WorkplacesChangedEvent(String cause) {
}
