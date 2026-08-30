package banghak.home.halley.domain.notification;

/**
 * 그룹 채널로 나가는 알림 (설계 I96).
 *
 * <p>모두 <b>한 매물에 딸립니다.</b> 시스템·배치 알림은 두지 않습니다 — 보낼 곳이 없습니다.
 * 웹훅은 그룹마다 있고, 그룹과 무관한 일은 알릴 채널이 없습니다.
 */
public enum NotificationEventType {

    /** 새 매물이 올라왔다. */
    PROPERTY_CREATED,
    /** 매물이 지워졌다 — 함께 보던 사람은 왜 사라졌는지 알아야 한다. */
    PROPERTY_DELETED,
    /** 누가 의견을 남겼다. */
    COMMENT_CREATED,
    /** 누가 공간의 쾌적함을 매겼다 — 총점이 달라진다. */
    COMFORT_SCORED,
    /** 생존 확인 배치가 판매완료를 감지했다. */
    LISTING_SOLD_OUT
}
