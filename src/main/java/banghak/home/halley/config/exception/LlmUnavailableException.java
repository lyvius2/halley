package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

/** LLM이 꺼져 있거나 응답을 쓸 수 없다 (설계 I61). */
public class LlmUnavailableException extends BusinessException {

    public LlmUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE",
                "AI 분석을 사용할 수 없습니다. LLM 연동 설정을 확인해 주세요");
    }
}
