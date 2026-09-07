package banghak.home.halley.application.event;

/** 매물에 대한 사람의 판단이 바뀌었음을 알리는 이벤트. */
public record PropertyInsightChanged(Long propertyId, Kind kind, String actorNickname,
                                     String detail, String reason) {

    public enum Kind { COMFORT_SCORE, COMMENT, EDIT }


    public static PropertyInsightChanged comfortScore(Long propertyId, String actorNickname, int score) {
        return new PropertyInsightChanged(propertyId, Kind.COMFORT_SCORE, actorNickname,
                String.valueOf(score), "쾌적함 점수 변경");
    }


    public static PropertyInsightChanged comment(Long propertyId, String actorNickname, String content) {
        return new PropertyInsightChanged(propertyId, Kind.COMMENT, actorNickname,
                content, "코멘트 변경");
    }

 /** 매물 제원이 바뀌었다. */
    public static PropertyInsightChanged edited(Long propertyId, String actorNickname) {
        return new PropertyInsightChanged(propertyId, Kind.EDIT, actorNickname, null, "매물 정보 수정");
    }
}
