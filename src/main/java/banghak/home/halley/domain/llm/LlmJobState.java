package banghak.home.halley.domain.llm;

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
