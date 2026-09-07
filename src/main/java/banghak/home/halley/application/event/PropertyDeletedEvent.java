package banghak.home.halley.application.event;

/** 매물이 지워졌다. */
public record PropertyDeletedEvent(Long groupId, String propertyName) {
}
