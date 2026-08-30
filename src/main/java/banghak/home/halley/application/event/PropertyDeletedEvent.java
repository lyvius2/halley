package banghak.home.halley.application.event;

/**
 * 매물이 지워졌다 (설계 I96).
 *
 * <p><b>그룹과 이름을 함께 싣습니다.</b> 알림은 커밋 뒤에 나가는데 그때는 매물이 이미 없어
 * 조회로는 어느 그룹이었는지도, 무엇이었는지도 알 수 없습니다.
 */
public record PropertyDeletedEvent(Long groupId, String propertyName) {
}
