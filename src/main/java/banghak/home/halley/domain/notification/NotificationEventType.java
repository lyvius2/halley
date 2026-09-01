package banghak.home.halley.domain.notification;

/**
 * 그룹 채널로 나가는 알림 (설계 I96).
 *
 * <p>모두 <b>한 매물에 딸립니다.</b> 시스템·배치 알림은 두지 않습니다 — 보낼 곳이 없습니다.
 * 웹훅은 그룹마다 있고, 그룹과 무관한 일은 알릴 채널이 없습니다.
 */
public enum NotificationEventType {

    /** 새 매물이 올라왔다. */
    PROPERTY_CREATED(""),
    /** 매물이 지워졌다 — 함께 보던 사람은 왜 사라졌는지 알아야 한다. */
    PROPERTY_DELETED(null),
    /** 누가 의견을 남겼다. */
    COMMENT_CREATED("/comments"),
    /** 누가 공간의 쾌적함을 매겼다 — 총점이 달라진다. */
    COMFORT_SCORED("/score"),
    /** 생존 확인 배치가 쓰던 값 (설계 I157에서 폐지). 지난 기록에 남아 있어 enum 은 유지한다 */
    LISTING_SOLD_OUT("");

    /**
     * 알림을 눌렀을 때 <b>열려야 할 곳</b> (설계 I201).
     *
     * <p>`/properties/{id}` 뒤에 붙습니다. 전에는 전부 매물 첫 화면으로 갔습니다 —
     * "누가 쾌적함을 평가했다"는 알림을 눌러도 <b>그 채점 화면까지 다시 찾아</b>
     * 들어가야 했습니다. 모달마다 주소가 생겨(I198) 가능해졌습니다.
     *
     * <p><b>null 이면 링크를 안 답니다.</b> 삭제 알림이 그렇습니다 — 이미 없는 매물입니다.
     */
    private final String linkSuffix;

    NotificationEventType(String linkSuffix) {
        this.linkSuffix = linkSuffix;
    }

    public String linkSuffix() {
        return linkSuffix;
    }
}
