package banghak.home.halley.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    @DisplayName("사진 업로드 용량을 넘으면 413과 구체적인 오류 코드를 반환한다")
    void returnsPayloadTooLargeForOversizedImage() {
        // given
        final MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(20_000_000L);
        final MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/properties/12/images");

        // when
        final ResponseEntity<Map<String, Object>> response = handler.handleImageTooLarge(exception, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody()).containsEntry("code", "PROPERTY_IMAGE_TOO_LARGE");
        assertThat(response.getBody()).containsEntry("message", "사진 용량이 업로드 한도를 초과했습니다");
    }
}
