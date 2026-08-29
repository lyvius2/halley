package banghak.home.halley.domain.llm;

/**
 * LLM 작업의 진행 상태 (설계 I72).
 *
 * <p>진행 중 표시와 결과를 <b>한 키에 함께</b> 둡니다. 마커와 결과를 따로 두면 폴링 한 번에
 * 두 군데를 봐야 하고, "진행 중인데 결과도 있는" 어중간한 조합이 생깁니다.
 *
 * @param status  RUNNING이면 아직 응답 전, DONE이면 결과가 담겨 있다
 * @param payload DONE일 때의 결과 JSON. RUNNING이면 null
 */
public record LlmJobState(Status status, String payload) {

    public enum Status {
        RUNNING,
        DONE
    }

    public static LlmJobState running() {
        return new LlmJobState(Status.RUNNING, null);
    }

    public static LlmJobState done(String payload) {
        return new LlmJobState(Status.DONE, payload);
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }
}
