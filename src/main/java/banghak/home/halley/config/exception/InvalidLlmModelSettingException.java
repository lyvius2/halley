package banghak.home.halley.config.exception;

import org.springframework.http.HttpStatus;

/** AI 모델 설정 요청이 잘못됐다 — 아는 자리가 아니거나, 목록 밖 모델이다 (설계 I267). */
public class InvalidLlmModelSettingException extends BusinessException {

    public InvalidLlmModelSettingException(String message) {
        super(HttpStatus.BAD_REQUEST, "LLM_MODEL_SETTING_INVALID", message);
    }
}
