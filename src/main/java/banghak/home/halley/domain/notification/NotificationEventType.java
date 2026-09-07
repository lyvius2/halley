package banghak.home.halley.domain.notification;

/** 그룹 채널로 나가는 알림. */
public enum NotificationEventType {

 /** 새 매물이 올라왔다. */
    PROPERTY_CREATED(""),
 /** 매물이 지워졌다. 함께 보던 사람은 왜 사라졌는지 알아야 한다. */
    PROPERTY_DELETED(null),
 /** 누가 의견을 남겼다. */
    COMMENT_CREATED("/comments"),
 /** 누가 공간의 쾌적함을 매겼다. 총점이 달라진다. */
    COMFORT_SCORED("/score"),
 /** 생존 확인 배치가 쓰던 값. 지난 기록에 남아 있어 enum 은 유지한다 */
    LISTING_SOLD_OUT("");

 /** 알림을 눌렀을 때 열려야 할 곳. */
    private final String linkSuffix;

    NotificationEventType(String linkSuffix) {
        this.linkSuffix = linkSuffix;
    }

    public String linkSuffix() {
        return linkSuffix;
    }
}
