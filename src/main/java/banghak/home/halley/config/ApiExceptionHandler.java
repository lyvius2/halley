package banghak.home.halley.config;

import banghak.home.halley.config.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleImageTooLarge(MaxUploadSizeExceededException ex,
                                                                   HttpServletRequest request) {
        log.warn("Image upload exceeded the multipart limit. path={}", request.getRequestURI());
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "PROPERTY_IMAGE_TOO_LARGE");
        body.put("message", "사진 용량이 업로드 한도를 초과했습니다");
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(body);
    }
}
