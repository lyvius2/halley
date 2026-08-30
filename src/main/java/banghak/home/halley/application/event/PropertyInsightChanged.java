package banghak.home.halley.application.event;

/**
 * 매물에 대한 <b>사람의 판단</b>이 바뀌었음을 알리는 이벤트 (설계 I78).
 *
 * <p>AI 추천도는 매물 제원만 보고 매기는 값이 아니라 <b>사용자들이 이 집을 어떻게 보는지</b>도
 * 재료로 씁니다. 쾌적함 점수와 코멘트가 그 재료이므로, 바뀌면 다시 물어야 합니다.
 *
 * <p>화면에서 '다시 물어보기' 버튼을 없앤 자리를 이 이벤트가 대신합니다. 사용자가 언제 눌러야
 * 하는지 알기 어려웠고, 누르지 않으면 옛 판단이 그대로 남았습니다.
 *
 * @param kind   무엇이 바뀌었는지. 재질의 트리거이자 <b>어떤 알림을 보낼지</b> 가르는 값이다
 * @param reason 로그에서 원인을 되짚기 위한 문구
 */
public record PropertyInsightChanged(Long propertyId, Kind kind, String actorNickname, String reason) {

    public enum Kind { COMFORT_SCORE, COMMENT }


    public static PropertyInsightChanged comfortScore(Long propertyId, String actorNickname) {
        return new PropertyInsightChanged(propertyId, Kind.COMFORT_SCORE, actorNickname, "쾌적함 점수 변경");
    }

    public static PropertyInsightChanged comment(Long propertyId, String actorNickname) {
        return new PropertyInsightChanged(propertyId, Kind.COMMENT, actorNickname, "코멘트 변경");
    }
}
