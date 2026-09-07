package banghak.home.halley.application.event;

/** 사용자가 추가되거나 직장 위치·활성 상태가 바뀌었다. */
public record WorkplacesChangedEvent(String cause) {
}
