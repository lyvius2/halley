package banghak.home.halley.application.event;

public record PropertyInsightChanged(Long propertyId, Kind kind, String actorNickname,
                                     String detail, String reason) {

    public enum Kind { COMFORT_SCORE, COMMENT, EDIT }


    public static PropertyInsightChanged comfortScore(Long propertyId, String actorNickname, int score) {
        return new PropertyInsightChanged(propertyId, Kind.COMFORT_SCORE, actorNickname,
                String.valueOf(score), "쾌적함 점수 변경");
    }

    /** @param content 남기거나 고친 글. 지운 경우에는 null — 실을 내용이 없다  */
    public static PropertyInsightChanged comment(Long propertyId, String actorNickname, String content) {
        return new PropertyInsightChanged(propertyId, Kind.COMMENT, actorNickname,
                content, "코멘트 변경");
    }

    public static PropertyInsightChanged edited(Long propertyId, String actorNickname) {
        return new PropertyInsightChanged(propertyId, Kind.EDIT, actorNickname, null, "매물 정보 수정");
    }
}
